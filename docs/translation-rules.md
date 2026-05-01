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
- Wrap multi-statement business operations in
  `dsl.transaction(cfg -> { ... })`. The lambda receives a jOOQ
  `Configuration` bound to the transaction; **all** queries inside the
  block must be executed via that configuration, e.g.
  `DSL.using(cfg).insertInto(ACCOUNT)...` or by accepting `Configuration`
  in the helper and calling `cfg.dsl().insertInto(...)`. Queries issued
  through the outer `dsl` instead run outside the transaction.
- CockroachDB returns serialization errors (`40001`) on contended
  transactions — wrap the whole `dsl.transaction(...)` call in the
  **CockroachDB retry helper** `CrdbRetry.run(dsl, () -> ...)` (introduced
  as part of the first program that needs it). Never re-throw `40001`.
- Read-only paths use plain `dsl.selectFrom(...)` returning records or
  `Optional`. Do **not** call `dsl.connection(c -> c.setReadOnly(true))`
  as a "read-only mode" knob — it only mutates the borrowed connection
  inside that callback and has no effect on subsequent `dsl` queries.
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
- Required fields on request-DTO records are expressed exclusively through
  `@NotNull` (and `@NotBlank` for non-blank strings such as customer
  name/address) on the record components, with `@Valid` on the controller
  parameter and on every nested DTO/Key reference. Do **not** put
  `Objects.requireNonNull(...)` checks inside the canonical constructor of
  a request DTO: those throw `NullPointerException` during Jackson
  deserialization, bypass `MethodArgumentNotValidException`, and surface
  as a 500 (`UNEX`) instead of the intended 400. Issues #10 and #15 are
  the empirical source for this rule.
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
    - `MethodArgumentNotValidException` (and `BindException`) → 400
    - `CbsaAbendException`      → 500 with `{ abendCode, message }`
    - everything else           → 500 with abend code `UNEX`.
- "Not found" is **never** an exception — see §6: controllers translate
  the commarea fail flag (e.g. `INQCUST-INQ-FAIL-CD = '1'`) into a
  `ResponseEntity` with status 404 directly.

## 9. Testing

- Unit-level: per-service tests using a Testcontainers CockroachDB instance
  and Flyway-migrated schema. Reuse a single container across tests via
  `@Testcontainers` + `@Container static`.
- Web-layer: `@SpringBootTest(webEnvironment = MOCK)` + `MockMvc`.
- Each program PR adds at least: success path, not-found path, validation
  failure path, and one invariant assertion (e.g. balance non-negative
  after operation).

## 10. Configuration: typed properties + startup validation (sortcode and friends)

- Every COBOL-shaped fixed-width identifier configured via Spring must be bound
  through a dedicated `@ConfigurationProperties` record with
  `jakarta.validation` annotations and `@Validated`, so malformed values fail
  application startup instead of surfacing later as request-time 500s.
- Keep zero-padded fixed-width identifiers as `String` end-to-end in domain,
  services, controllers, DTOs, and tests. Convert to `int` only at the jOOQ
  persistence boundary when a column truly requires it, and only after a regex
  constraint has guaranteed the value is exactly six digits.
- When reading a fixed-width numeric identifier back from a numeric persistence
  column, always zero-pad it back to its COBOL width before returning it to the
  domain or wire contract.
- Use `[0-9]{6}` (explicit ASCII range) rather than `\d{6}` in
  `@Pattern(regexp = ...)` annotations. Java's `\d` can match non-ASCII Unicode
  digits when `UNICODE_CHARACTER_CLASS` is enabled in the validator runtime,
  which would let unexpected values pass and then fail downstream string
  comparisons or JDBC lookups.
- Enforce the same six-digit ASCII invariant on the read side too: domain
  records that carry a sortcode (e.g. `AccountDetails`, `CustomerDetails`)
  validate `[0-9]{6}` in their canonical constructor so controllers do not
  need to re-pad or re-validate before serialising. Any non-conforming value
  read from the database fails loudly at the repository→domain boundary.
- YAML coerces unquoted values that look numeric (`012345`) into integers,
  which silently drops leading zeros and then fails `[0-9]{6}` startup
  validation. Always quote zero-padded fixed-width identifiers in
  `application.yaml` (and any environment-variable defaults), e.g.
  `cbsa.sortcode: "987654"`.
- Issues #11 and #17 are the empirical source for this rule: parsing sortcodes
  with `Integer.parseInt(...)` caused runtime 500s and could silently lose
  leading zeros.

## 11. PR / commit conventions

- One COBOL program per branch and per PR. Branch name: `migrate/<PROGRAM>`.
- Commit subject: `feat(<program>): translate <PROGRAM> to Java`.
- PR body: brief rationale, link to the source `.cbl` file, list of
  follow-ups (e.g. "hash-sharding deferred until perf data").
- The Reviewer posts exactly `LGTM` to approve, or a comment starting with
  `BLOCK:` listing each blocking issue on its own line.

## 12. PROCTRAN audit-trail write failures

Every program in this codebase writes a `PROCTRAN` row as part of the
mutating transaction it performs (account/customer create, update, delete,
debit/credit, transfer). A failure to write `PROCTRAN` is **not** a
domain-level failure of the operation; it is a system abend that must be
escalated to operations.

- A non-retryable `DataAccessException` thrown by a `PROCTRAN` insert MUST
  be wrapped in `CbsaAbendException(PROCTRAN_ABEND_CODE, ...)` where
  `PROCTRAN_ABEND_CODE = "HWPT"`. The `@ControllerAdvice` then surfaces
  this as a 500 abend, which is the right classification for an
  audit-trail outage.
- Do not map `PROCTRAN` insert failures to a domain "fail code" (e.g.
  CRECUST `"1"`, UPDCUST `"3"`). Doing so makes an audit-write outage
  indistinguishable from a real data-mutation failure and silently
  downgrades a 500 to a 200-with-fail-code.
- Always re-throw `SQLSTATE 40001` (serialization failures) so the
  surrounding `CrdbRetry` wrapper can retry the whole transaction.
  Wrap *only* the non-retryable branch:
  ```java
  } catch (DataAccessException exception) {
      if (isSerializationFailure(exception)) {
          throw exception;
      }
      throw new CbsaAbendException(PROCTRAN_ABEND_CODE,
              "<PROGRAM> failed to write the audit trail.", exception);
  }
  ```
- The `DataAccessException` here is **`org.springframework.dao.DataAccessException`**,
  not `org.jooq.exception.DataAccessException`. Spring Boot 3's
  `JooqAutoConfiguration` registers `DefaultExceptionTranslatorExecuteListener`,
  which intercepts every `SQLException` raised by jOOQ and substitutes the
  Spring DAE before it leaves jOOQ. Spring's DAE is **not** a subtype of
  jOOQ's, so a `catch (org.jooq.exception.DataAccessException)` block never
  matches at runtime. `CrdbRetry.run(...)`, every PROCTRAN inner catch, and
  the outer `XRTY` catch must all import `org.springframework.dao.DataAccessException`.
- Use the constant name `PROCTRAN_ABEND_CODE` (not `ABEND_CODE`,
  `WPCD`, or any program-specific spelling) so the intent of the catch
  block is obvious in code review.
- The wrap may live in either the repository or the service layer,
  whichever owns the `PROCTRAN` insert call site:
  - Repositories that own their own transaction (e.g. `CreaccRepository`,
    `CrecustRepository`, `UpdcustRepository`) wrap the `PROCTRAN`
    `try/catch` directly inside the transactional method.
  - Services that drive a `TransactionTemplate` themselves (e.g.
    `DbcrfunService`, `DelaccService`, `DelcusService`, `XfrfunService`)
    own the transaction; the wrap may live either in the service (e.g.
    `DelaccService`, `DelcusService`, `XfrfunService`) or in the
    repository's `insertProcTran(...)` method called from inside that
    template (e.g. `DbcrfunRepository`).
- `PROCTRAN_ABEND_CODE` is for PROCTRAN insert failures only. Reserve a
  separate `RETRY_EXHAUSTED_ABEND_CODE = "XRTY"` for the outer
  `catch (DataAccessException)` around `CrdbRetry.run(...)` so a
  serialization-retry exhaustion does not get reported as `HWPT`. Any
  other `DataAccessException` that escapes `CrdbRetry.run` (in practice
  none, since inner catches handle each call site) should be re-thrown
  and surfaced via the global `Exception` handler as `UNEX`.
- Issues #12 and #19 are the empirical source for this rule: PROCTRAN
  failures in CRECUST / UPDCUST were originally returning fail codes
  `"1"` / `"3"`, hiding audit-trail outages behind a 200 OK response.

## 13. Recurring patterns from PR #4 review

These seven patterns came out of the six review rounds on the INQCUST POC
(PR #4) and the cleanup pass that followed (issues #5, #6). Treat each one
as a hard rule for every program PR.

1. **Single error wire shape: `ProblemDetail` (RFC 7807) end-to-end.**
   Successful responses are domain DTOs; *every* error response (4xx/5xx)
   is a `org.springframework.http.ProblemDetail`. The `@ControllerAdvice`
   (`CbsaExceptionHandler`) builds the body for thrown exceptions
   (`MethodArgumentNotValidException` → 400, `CbsaAbendException` → 500,
   `Exception` → 500/`UNEX`); controllers that translate a domain "fail"
   result do so with the same shape:
   ```java
   ProblemDetail pd = ProblemDetail.forStatus(status);
   pd.setTitle("<Program> <intent> failed");
   pd.setDetail(result.message());
   pd.setProperty("failCode", result.failCode());
   return ResponseEntity.status(status).body(pd);
   ```
   No per-program legacy result-record (e.g. a `{failCode,message}`
   record) leaks to the wire on the error path.
2. **Defensive null-checks at service-method boundaries.** Every public
   `*Service` method begins with
   `Objects.requireNonNull(arg, "arg must not be null")` for each
   parameter, and every constructor of an immutable result record
   (`*Result.success(...)`, `*Result.failure(...)`) null-checks its
   arguments before assigning fields. This catches programming errors at
   the boundary instead of letting them surface as NPEs deep inside a
   transaction.
3. **`@Valid` on every `@RequestBody`.** Any controller method that
   accepts a `@RequestBody` DTO must mark the parameter `@Valid`, and
   every nested DTO/Key reference inside that DTO must also be `@Valid`.
   Validation annotations on a DTO that is never `@Valid`-bound are dead
   code. Validation lives on the record components (`@NotNull`,
   `@NotBlank`, `@Size`, `@Pattern`), **not** as `Objects.requireNonNull`
   inside the canonical constructor — see §6 for why.
4. **Deterministic randomness via injected source / clock.** Services
   must not call `ThreadLocalRandom`, `Math.random`, `new Random()`, or
   `Instant.now()` directly. Inject a `Random` bean (e.g.
   `@Qualifier("applicationRandom")`), a `Clock` bean, or a small
   per-domain interface (`RandomCustomerNumberGenerator`) and provide a
   deterministic test double in unit and web-MVC tests. Integration
   tests must not depend on probabilistic outcomes.
5. **Retry off-by-one: random-exhaustion vs not-found.** When a bounded
   retry loop in random-pick mode exhausts without finding a target, the
   response is a distinct "retry exhausted" outcome (fail code `"R"` →
   HTTP 503), not a generic backend abend. "Exact key not found" remains
   fail code `"1"` → HTTP 404. Controllers MUST distinguish the two via
   `result.isRandomRetryExhaustedFailure()` / `result.isNotFoundFailure()`
   helpers on the result record.
6. **Control-baseline preservation in tests and writes.** The `control`
   table holds Flyway-seeded invariants (sortcode, last_*_number,
   *_count). Production code MUST `UPDATE` rather than `DELETE`+insert.
   Integration-test cleanup MUST either skip `control` entirely
   (preferred) or re-insert the canonical baseline row after deleting.
   The canonical baseline row is sortcode `"987654"` with all
   counters/last-numbers `0`.
7. **Empty-table → 404, not 500.** For read paths, an empty target
   table is semantically "not found", matching the COBOL behavior where
   an empty file produces the same fail code as a missing record. Map
   empty-table outcomes to the regular not-found fail code (`"1"` →
   404), never to a backend-failure code or a 500.

Issues #5 and #6 (with PR #4 as the empirical source) document why each
of these rules exists.

## 14. Translator checklist

Quick scan before opening a program PR. Every item must hold.

- [ ] Public `*Service` methods start with `Objects.requireNonNull(...)`
      on each parameter.
- [ ] Every `@RequestBody` DTO param is annotated `@Valid`; nested DTOs
      reachable from it carry `@Valid` on their references.
- [ ] Required record components use `@NotNull` / `@NotBlank` / `@Size`
      / `@Pattern`; no `requireNonNull` inside record canonical
      constructors of request DTOs.
- [ ] All error paths return `ProblemDetail` — no per-program error
      record on the wire.
- [ ] Stochastic behavior uses an injected `Random` / `Clock` /
      generator interface with a deterministic test double.
- [ ] Retry-exhaustion → fail code `"R"` → 503; not-found → `"1"` →
      404; empty-table → `"1"` → 404.
- [ ] `control` is `UPDATE`d, never `DELETE`+inserted in production
      code; tests skip `control` or re-seed the baseline row.
- [ ] PROCTRAN insert failures are wrapped as
      `CbsaAbendException(PROCTRAN_ABEND_CODE, ...)` (§12); 40001 is
      re-thrown for `CrdbRetry`.
- [ ] Sortcode and other zero-padded fixed-width identifiers are quoted
      in YAML, validated `[0-9]{6}`, kept as `String` end-to-end (§10).

## 15. Out of scope

`BNK1*`, `BNKMENU` (BMS terminal handlers), `BANKDATA` (seed loader; replaced
by Flyway), and `ABNDPROC` (replaced by `@ControllerAdvice`) are dropped and
must not be translated.
