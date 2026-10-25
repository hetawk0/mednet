# PostgreSQL setup and integration testing

MedNet persists accounts, care workflows and audit events in PostgreSQL. Flyway migrations in
`src/main/resources/db/migration` create the schema on API startup. The default `mvn test` suite uses
an in-memory H2 database in PostgreSQL mode; the PostgreSQL profile below runs the same workflow suite
against real PostgreSQL. Never put real credentials in this file, in source or in reports.

## Databases and roles

Use one role per purpose. None of these roles needs superuser, `CREATEDB` or `CREATEROLE`.

| Purpose | Database | Owner role | Settings live in |
|---|---|---|---|
| Local development API | `mednet` | `mednet` | `.env` (`SPRING_DATASOURCE_*`) |
| Maven PostgreSQL integration tests | `mednet_test` | `mednet_test` | `.env.test.local` (`MEDNET_TEST_DATABASE_*`) |
| Live-API browser tests | `mednet_e2e_test` | `mednet_test` | `.env.test.local` (`MEDNET_E2E_DATABASE_URL`) |

`.env` and `.env.test.local` match the `.env.*` rule in `.gitignore`; only `.env.example` (placeholders)
is committed. Production uses its own database, role and secret store and must never appear in these
files.

## Environment variables

| Variable | Used by | Example (placeholder) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | API | `jdbc:postgresql://localhost:5432/mednet` |
| `SPRING_DATASOURCE_USERNAME` | API | `mednet` |
| `SPRING_DATASOURCE_PASSWORD` | API | `replace-with-a-local-only-password` |
| `MEDNET_TEST_DATABASE_URL` | `mvn -Ppostgres-it verify` | `jdbc:postgresql://localhost:5432/mednet_test` |
| `MEDNET_TEST_DATABASE_USERNAME` | `mvn -Ppostgres-it verify` | `mednet_test` |
| `MEDNET_TEST_DATABASE_PASSWORD` | `mvn -Ppostgres-it verify` | `replace-with-a-local-test-password` |
| `MEDNET_E2E_DATABASE_URL` | live-API browser runs (passed to the API as `SPRING_DATASOURCE_URL`) | `jdbc:postgresql://localhost:5432/mednet_e2e_test` |

When `SPRING_DATASOURCE_URL` is absent the API still starts (health checks and the configured super
administrator work) and database-backed workflows report `NOT_CONFIGURED`. When it is present but the
database rejects the login, startup fails after Flyway's 10 connection retries with PostgreSQL's own
message (for example `password authentication failed for user "mednet"`), which names the user but never
the password.

## One-time local provisioning

Run as a PostgreSQL superuser in `psql` (for example `psql -U postgres -d postgres`). Each statement
only creates what is missing; it does not change existing roles or databases. Set each password with
psql's `\password`, which sends a SCRAM-SHA-256 hash so the plain text never reaches the server log.

```sql
CREATE ROLE mednet LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 20;
\password mednet
CREATE ROLE mednet_test LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 20;
\password mednet_test

CREATE DATABASE mednet OWNER mednet;
CREATE DATABASE mednet_test OWNER mednet_test;
CREATE DATABASE mednet_e2e_test OWNER mednet_test;
REVOKE CONNECT, TEMPORARY ON DATABASE mednet FROM PUBLIC;
REVOKE CONNECT, TEMPORARY ON DATABASE mednet_test FROM PUBLIC;
REVOKE CONNECT, TEMPORARY ON DATABASE mednet_e2e_test FROM PUBLIC;
```

Keep `pg_hba.conf` on `scram-sha-256`; do not switch to `trust` to make local tests pass. Then write the
passwords into `.env` and `.env.test.local` and check each login:

```sh
psql -h localhost -U mednet -d mednet -c "select current_user, current_database()"
psql -h localhost -U mednet_test -d mednet_test -c "select current_user, current_database()"
```

Because PostgreSQL 15+ owns the `public` schema through `pg_database_owner`, each owner role can create
the Flyway tables in its own database without further grants.
On PostgreSQL 14 and earlier, explicitly assign the test database's `public` schema to its test role
before running the profile (the GitHub Actions job does this explicitly):

```sql
ALTER SCHEMA public OWNER TO mednet_test;
```

## Credential rotation (open since 2026-10-11)

The Supabase database password used in Phase 5A was exposed in a chat and must be rotated before any
further PostgreSQL run. Until then, Phase 5B ran no database-backed tests. After rotation:

1. The project owner resets the database password in the Supabase dashboard (Project Settings →
   Database) and writes the new value into `.env` (`SPRING_DATASOURCE_PASSWORD`) and
   `.env.test.local` (`MEDNET_TEST_DATABASE_PASSWORD`) without quotes and without pasting it anywhere
   else. Any deployment or tool that connects to the project needs the same update.
2. Check the new value connects (`select 1`) and that the old one is refused, without printing either.
3. Re-run `mvn -Ppostgres-it verify` and the live-API suites against the `_test` databases only.
   `PostgreSqlWorkflowIT` inherits the Phase 5B security tests in `MedNetApplicationTests` (deny by
   default, sign-up takeover). Rate limits stay on during these runs (defaults did not trip in the
   H2 runs); if a long local run trips them, restart the API rather than disabling limits.

Treat the project's `postgres` database as possibly holding real data: no resets, destructive
migrations, automated tests or account changes there, and confirm backups (Supabase daily backups or
point-in-time recovery, depending on plan) before relying on it.

## Hosted alternative: Supabase (used in Phase 5A)

A disposable Supabase project can stand in for the local server. Connect through the session-mode pooler
(`aws-0-<region>.pooler.supabase.com:5432`, user `postgres.<project-ref>`, `?sslmode=require`); the
transaction-mode port 6543 does not suit JDBC prepared statements or Flyway. Supabase gives one database,
`postgres`, and a non-superuser `postgres` role with `CREATEDB`, so create the test databases as separate
databases owned by that role:

```sql
CREATE DATABASE mednet_test;
CREATE DATABASE mednet_e2e_test;
```

Keep tests out of the `postgres` database: the API's development settings in `.env` point there. `.env`
is read as a Java properties file, so write values without quotes. Network latency makes the workflow
suite take about six minutes instead of fifteen seconds.

## Running the checks

H2 (no PostgreSQL needed):

```sh
mvn test
```

PostgreSQL integration profile (Bash; load the test settings without echoing them):

```sh
set -a; . ./.env.test.local; set +a
mvn -Ppostgres-it verify
```

`verify` runs the H2 suite first, then, through Failsafe:

- `PostgreSqlIntegrationIT`: migrations apply and validate on PostgreSQL; an account round-trips.
- `PostgreSqlWorkflowIT`: every workflow test in `MedNetApplicationTests` (authentication, roles,
  appointments, consultations, records and consent, laboratory review and release, administration,
  audit) against PostgreSQL.

**The test database is emptied at the start of each run.** `PostgreSqlTestDatabase` drops and recreates
the `public` schema of the test database and lets Flyway migrate from scratch. It refuses to run unless the
database name ends in `_test`, the role is not a superuser and the role owns the database. It deliberately
does not use `DROP OWNED BY`: that also revokes the role's privileges on shared objects such as other
databases, which matters when the role is shared (for example Supabase's `postgres` role).

Live-API browser tests against PostgreSQL: empty the e2e database, then start the API on it with a
throwaway administrator (see `web/e2e/README.md` for the browser side):

```sh
set -a; . ./.env.test.local; set +a
PGPASSWORD="$MEDNET_TEST_DATABASE_PASSWORD" psql -U "$MEDNET_TEST_DATABASE_USERNAME" -d "${MEDNET_E2E_DATABASE_URL#jdbc:}"   -c "DROP SCHEMA IF EXISTS public CASCADE" -c "CREATE SCHEMA public"
SPRING_DATASOURCE_URL="$MEDNET_E2E_DATABASE_URL" \
SPRING_DATASOURCE_USERNAME="$MEDNET_TEST_DATABASE_USERNAME" \
SPRING_DATASOURCE_PASSWORD="$MEDNET_TEST_DATABASE_PASSWORD" \
MEDNET_ADMIN_EMAIL=e2e-admin@mednet.test MEDNET_ADMIN_PASSWORD="<generated-for-this-run>" \
SERVER_PORT=8081 SESSION_COOKIE_SECURE=false JAVA_TOOL_OPTIONS=-Xmx768m mvn spring-boot:run
```

Environment variables override the values imported from `.env`, so the development database is not
touched.

## Compatibility review (updated 2026-10-26)

- Most repository queries use JPQL; the laboratory review queue uses a native aggregate query and the
  PostgreSQL rate-limit store uses atomic `INSERT ... ON CONFLICT ... RETURNING`. The review queue
  projects its oldest timestamp as epoch milliseconds for stable PostgreSQL/H2 conversion.
- Migrations use portable types: `VARCHAR`, `BOOLEAN`, `INTEGER`, `NUMERIC(12,3)`, `DATE`, `TIME`,
  `TIMESTAMP WITH TIME ZONE` (mapped from `Instant`, microsecond precision in both databases). V14 uses
  PostgreSQL `UPDATE … FROM`, which H2's PostgreSQL mode also accepts. V21 adds JDBC sessions and shared
  rate-limit windows.
- Statuses and roles are `VARCHAR` with `CHECK` constraints, not database enum types, so enum handling
  is identical.
- Identifiers are application-generated UUID strings; there are no sequences or identity columns.
- V3, V8 and V9 drop PostgreSQL's generated check-constraint names and H2's (`CONSTRAINT_9186`,
  `CONSTRAINT_F6D704`) with `IF EXISTS`, so both databases end with one named constraint.
- The full backend and workflow profile passes against isolated PostgreSQL 14. Ordering ties on equal
  `created_at` and transaction isolation under concurrent workflow updates still need dedicated tests.
