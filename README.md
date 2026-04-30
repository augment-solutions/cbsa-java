# cbsa-java

Spring Boot 3.5 / Java 25 / CockroachDB modernization of the CICS Banking
Sample Application (CBSA). The original COBOL/CICS source — read-only — lives
at [`augment-solutions/cics-banking-sample-application-cbsa`][src].

This repository is produced by the Cosmos COBOL→Java migration agents.

[src]: https://github.com/augment-solutions/cics-banking-sample-application-cbsa

## Stack

- Java 25 (Temurin), Spring Boot 3.5
- CockroachDB v24.3 (PostgreSQL wire protocol)
- jOOQ 3.20 for typesafe SQL, Flyway 11 for schema migrations
- Testcontainers + JUnit 5 for integration tests

## Local development

Prereqs: JDK 25, Maven 3.9+, CockroachDB v24.3.

```bash
cockroach start-single-node --insecure \
  --store=type=mem,size=4GiB --listen-addr=localhost:26257 --background
cockroach sql --insecure --host=localhost:26257 \
  -e "CREATE DATABASE IF NOT EXISTS cbsa"

./mvnw -B verify
./mvnw spring-boot:run
```

The build runs Flyway migrations against the running CockroachDB and then
generates the jOOQ classes into `target/generated-sources/jooq` (not
committed).

## Migration provenance & rules

The translation rulebook is at [`docs/translation-rules.md`](docs/translation-rules.md).
Every program PR must conform to it; the Reviewer enforces it.

The full migration ledger is tracked in the pinned issue
**"CBSA Migration Ledger"** in this repository.
