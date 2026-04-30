# CBSA COBOL → Java Translation Rules

This rulebook is authoritative for the CBSA migration. The Translator
follows it when producing Java; the Reviewer enforces it when reviewing PRs.
Source repo (read-only): `augment-solutions/cics-banking-sample-application-cbsa`.
Target repo: `augment-solutions/cbsa-java`. Stack: Java 25, Spring Boot 3.5,
jOOQ 3.20, CockroachDB v24.3 (PostgreSQL wire protocol).

## 1. Project layout

```
src/main/java/com/augment/cbsa/
  CbsaApplication.java         -- Spring Boot entrypoint
  config/                      -- @Configuration beans
  web/                         -- @RestController + DTOs (one package per program)
  service/                     -- @Service classes (one per COBOL program)
  repository/                  -- jOOQ-based persistence; one repo per aggregate
  domain/                      -- value types, enums (PROC-TRAN-TYPE, etc.)
  error/                       -- exceptions + @ControllerAdvice (replaces ABNDPROC)
src/main/resources/
  application.yaml             -- env-driven config
  db/migration/V*.sql          -- Flyway migrations
src/test/java/com/augment/cbsa/ -- JUnit 5 + Testcontainers + MockMvc
```

One COBOL program → one `*Service` class + one `*Controller` exposing a REST
endpoint. The COBOL commarea (`DFHCOMMAREA`) maps to a request DTO; the
returned commarea maps to a response DTO. See §6 for the REST contract.

## 2. PIC clause → Java/SQL mapping

| COBOL PIC                  | SQL column     | Java type        |
|----------------------------|----------------|------------------|
| `PIC 9(n)` n ≤ 4           | SMALLINT       | `short`/`Short`  |
| `PIC 9(n)` 5 ≤ n ≤ 9       | INTEGER        | `int`/`Integer`  |
| `PIC 9(n)` 10 ≤ n ≤ 18     | BIGINT         | `long`/`Long`    |
| `PIC S9(n)V99` 5 ≤ n+2 ≤ 18| NUMERIC(n+2,2) | `BigDecimal`     |
| `PIC X(n)`                 | VARCHAR(n)     | `String`         |
| `PIC 9(8)` date (DDMMYYYY) | DATE           | `LocalDate`      |
| `PIC 9(6)` time (HHMMSS)   | TIME           | `LocalTime`      |
| `PIC X` flag (`Y`/`N`)     | BOOLEAN        | `boolean`        |
| `PIC 9(6) DISPLAY` sortcode| CHAR(6)        | `String` (zero-padded) |

Money columns (balances, amounts, overdraft limits, interest rates) are
**always** `BigDecimal`; never `double`. Use `setScale(2, HALF_EVEN)` whenever
intermediates are exposed.

## 3. REDEFINES

REDEFINES are a memory-overlay trick. Treat the underlying scalar (e.g.
`CUSTOMER-DATE-OF-BIRTH PIC 9(8)`) as the canonical persisted form and expose
the decomposed view (`-DD`, `-MM`, `-YYYY`) only when the API contract
demands it. Use `LocalDate.of(yyyy, mm, dd)` and `date.getDayOfMonth()` etc.
Never persist both forms.

## 4. CICS idioms

| CICS construct                            | Java replacement                         |
|-------------------------------------------|------------------------------------------|
| `EXEC CICS READ FILE(...)` / `WRITE`      | jOOQ `selectFrom`/`insertInto`/`update`  |
| `EXEC CICS LINK PROGRAM(...)`             | direct method call between `@Service`s   |
| `EXEC CICS RETURN COMMAREA(...)`          | controller method `return ResponseDTO`   |
| `EXEC CICS HANDLE ABEND`                  | typed exception + `@ControllerAdvice`    |
| `EXEC CICS ABEND ABCODE('xxxx')`          | `throw new CbsaAbendException("xxxx")`   |
| `EXEC CICS ENQ` / `DEQ` (NCS counters)    | `SELECT ... FOR UPDATE` row-lock or     |
|                                            | `INSERT ... ON CONFLICT` for counters    |
| `EXEC CICS ASKTIME` / `FORMATTIME`        | `LocalDate.now()` / `LocalTime.now()`    |
| `DFHRESP(NORMAL)` checks                  | exceptions thrown by jOOQ; do not retry  |
|                                            | on success                               |
| `DFHRESP(SYSIDERR)` 100× retry loop       | drop. CockroachDB has its own retries.   |
| `DFHRESP(NOTFND)`                          | jOOQ returns `Optional.empty()` →       |
|                                            | populate response with fail code         |

## 5. CockroachDB / jOOQ conventions

- All persistence goes through generated jOOQ `DSLContext` + table records
  in `com.augment.cbsa.jooq`. **Never** hand-write JDBC SQL.
- Wrap multi-statement business operations in `dsl.transaction(c -> { ... })`
  blocks. CockroachDB returns serialization errors (`40001`) on contended
  transactions — wrap with the **CockroachDB retry helper**
  `CrdbRetry.run(dsl, () -> ...)` (introduced as part of the first program
  that needs it). Never re-throw `40001`.
- Read-only paths use `dsl.connection(c -> c.setReadOnly(true))` only when
  beneficial; otherwise plain `dsl.selectFrom(...)`.
- Sequence-allocation idioms (NCS `HBNKCUST`, `HBNKACCT`) are replaced by an
  UPSERT against `control`:
  `UPDATE control SET customer_last = customer_last + 1 RETURNING customer_last`.
  This is atomic in CockroachDB.
- For high-write tables (`proctran`), the primary key is hash-sharded
  (`USING HASH WITH (bucket_count = 16)`) — see `V0__baseline.sql`.
- jOOQ generated code lives at `target/generated-sources/jooq` (not
  committed). Codegen runs in `generate-sources` after Flyway `migrate`.

## 6. REST contract

- Each program gets a route under `/api/v1/<program-lowercase>`.
- Method follows intent: `INQ*` → `GET`, `CRE*` → `POST`,
  `UPD*` → `PUT`, `DEL*` → `DELETE`, `XFRFUN`/`DBCRFUN` → `POST`.
- Path params for natural keys (e.g. `GET /api/v1/inqcust/{customerNumber}`).
- Request/response DTOs are Java `record`s in
  `com.augment.cbsa.web.<program>.dto`. Field names match the camelCase form
  of the commarea (e.g. `INQCUST-CUSTNO` → `customerNumber`).
- Validation via `jakarta.validation` annotations; constraint violations
  yield HTTP 400 from the `@ControllerAdvice`.
- A "not found" commarea result (e.g. `INQCUST-INQ-FAIL-CD = '1'`) maps to
  HTTP 404 with a `{ failCode, message }` body, **not** an exception.
- Hard failures (commarea-style abend) map to HTTP 500 with a structured
  body that includes the original 4-char abend code.

## 7. Concurrency / executors

- Spring Boot is configured with `spring.threads.virtual.enabled=true`. Use
  virtual threads for I/O. Do **not** create platform-thread executors by
  hand. For the async credit-agency programs (CRDTAGY1..5), use
  `@Async` plus a `VirtualThreadTaskExecutor` bean.
- Never `Thread.sleep` to mimic CICS `DELAY FOR SECONDS(...)` retries; rely
  on CockroachDB's own retry semantics.

## 8. Error model

- Define `CbsaAbendException(String abendCode, String message)` once. Each
  program throws it with its program-specific abend code (e.g. `CVR1` for
  INQCUST VSAM read failure) only for genuinely unrecoverable errors.
- A single `@ControllerAdvice` (`CbsaExceptionHandler`) maps:
    - `CbsaNotFoundException`  → 404
    - `MethodArgumentNotValid`  → 400
    - `CbsaAbendException`      → 500 with `{ abendCode, message }`
    - everything else           → 500 with abend code `UNEX`.

## 9. Testing

- Unit-level: per-service tests using a Testcontainers CockroachDB instance
  and Flyway-migrated schema. Reuse a single container across tests via
  `@Testcontainers` + `@Container static`.
- Web-layer: `@SpringBootTest(webEnvironment = MOCK)` + `MockMvc`.
- Each program PR adds at least: success path, not-found path, validation
  failure path, and one invariant assertion (e.g. balance non-negative
  after operation).

## 10. PR / commit conventions

- One COBOL program per branch and per PR. Branch name: `migrate/<PROGRAM>`.
- Commit subject: `feat(<program>): translate <PROGRAM> to Java`.
- PR body: brief rationale, link to the source `.cbl` file, list of
  follow-ups (e.g. "hash-sharding deferred until perf data").
- The Reviewer posts exactly `LGTM` to approve, or a comment starting with
  `BLOCK:` listing each blocking issue on its own line.

## 11. Out of scope

`BNK1*`, `BNKMENU` (BMS terminal handlers), `BANKDATA` (seed loader; replaced
by Flyway), and `ABNDPROC` (replaced by `@ControllerAdvice`) are dropped and
must not be translated.
