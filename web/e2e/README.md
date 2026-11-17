# Browser tests

`npm run test:e2e` runs every `*.spec.ts` at 390, 820 and 1440px against `next dev` (started on
port 3100, or reused if one is already running there). These specs stub the API in the browser
where they need one, so no backend is required.

## Live API specs (`*.live.spec.ts`)

These drive a real Spring Boot API with no mocks. They are skipped unless `MEDNET_LIVE_API=1`.

Run them against a disposable database only. The H2 file database below is what Phase 4A used; the
H2 jar comes from the local Maven cache (it is a test dependency).

1. Start the API from the repository root on a free port with a throwaway H2 file database, a
   local-only super admin and email delivery off:

   ```sh
   SERVER_PORT=8081 \
   SPRING_DATASOURCE_URL="jdbc:h2:file:/tmp/mednet-live/mednet;MODE=PostgreSQL;AUTO_SERVER=TRUE" \
   SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD= \
   MEDNET_ADMIN_EMAIL=live-admin@mednet.test MEDNET_ADMIN_PASSWORD='local-only-password' \
   GOOGLE_OAUTH_ENABLED=false EKDSEND_API_KEY= \
   mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true
   ```

2. Serve the web app on the same origin as the API. Either `next dev` with
   `MEDNET_API_URL=http://localhost:8081`, or a production build (`npm run build && npx next start`)
   behind a proxy that sends `/api/*` to the API and everything else to Next.js, as LPAD/nginx does.

3. Run the specs:

   ```sh
   MEDNET_LIVE_API=1 PLAYWRIGHT_BASE_URL=http://localhost:3100 \
   MEDNET_LIVE_H2_URL="jdbc:h2:file:/tmp/mednet-live/mednet;MODE=PostgreSQL;AUTO_SERVER=TRUE" \
   MEDNET_LIVE_H2_JAR=~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar \
   MEDNET_LIVE_ADMIN_EMAIL=live-admin@mednet.test MEDNET_LIVE_ADMIN_PASSWORD='local-only-password' \
   npx playwright test e2e/auth.live.spec.ts --workers=1
   ```

`PLAYWRIGHT_BASE_URL` skips Playwright's own `next dev` server.

**PostgreSQL instead of H2 (Phase 5A).** Start the API on a disposable PostgreSQL database whose name ends
in `_test` (see `docs/POSTGRESQL_SETUP.md`) and replace the two `MEDNET_LIVE_H2_*` variables with
`MEDNET_LIVE_PG_URL` (the JDBC URL), `MEDNET_LIVE_PG_USER`, `MEDNET_LIVE_PG_PASSWORD` and, if `psql` is not on
the path, `MEDNET_LIVE_PSQL`. `support/live-backend.ts` then writes the stand-in email tokens with `psql`,
passing the password through the environment; it refuses any database whose name does not end in `_test`,
and any host other than `localhost`, `127.0.0.1` or `::1` unless `MEDNET_LIVE_PG_ALLOW_REMOTE=1`.

**Use a production build for the web app.** `next dev` runs effects twice (React Strict Mode), so the
one-time `/verify?token=` link is consumed by the first request and the page shows the second request's
"invalid or expired" error even though the account was verified. A production build only proxies `/api/*`
in development, so put a same-origin reverse proxy in front of `next start` (as LPAD/nginx does in
production).

Because email delivery is off, `support/live-backend.ts` stands in for the inbox: it stores a known
verification token or reset code in the disposable database, hashed the way `AuthTokenService`
does. The real endpoints still perform every check. Without `MEDNET_LIVE_H2_*` those tests skip.

The configured super admin can sign in repeatedly since the Phase 4E fix, so the admin test runs in every
viewport project against one API process.

### Home care (`home-care.live.spec.ts`, Phase 5C)

Needs only `MEDNET_LIVE_ADMIN_EMAIL`/`MEDNET_LIVE_ADMIN_PASSWORD`. Creates synthetic patient, administrator,
two home-care and one laboratory account, then drives patient request → administrator assignment →
schedule, start and complete in the home-care UI → patient status, plus refusals for every other account
and a request the patient cancels while it is open.

### Pagination (`pagination.live.spec.ts`, Phase 5D)

Seeds more than 100 providers, laboratory results and queue rows through the real API (about 30 s on H2)
and checks paging, server-side filters, the doctor's review queue and role boundaries. Runs in the 1440px
project only; use a fresh disposable database, as each run adds 100+ rows.

### Directory and booking (`find-care.live.spec.ts`)

Needs only `MEDNET_LIVE_ADMIN_EMAIL`/`MEDNET_LIVE_ADMIN_PASSWORD` (no H2 access). `support/seed-directory.ts`
creates accounts, approves providers and publishes availability through the real admin and provider
endpoints. It saves the admin session in the OS temp directory (`mednet-live-admin-state.json`) and
reuses it, so both live specs can run against one API process despite the admin sign-in defect. Each
run adds new uniquely named providers and patients to the disposable database.

### Patient workspace (`patient.live.spec.ts`)

Needs the same admin variables as `find-care.live.spec.ts`. It seeds two doctors and a patient through
the real endpoints, then drives the patient workspace through the UI while the doctor's actions
(confirm, propose a new time, add a record, reply) use the doctor's own API session. Run with
`--workers=1`; the tests in each viewport project are serial and share one patient.

### Doctor workspace and cross-workspace flows (`provider.live.spec.ts`)

Same admin variables. Seeds an approved doctor with four open times, two patients with completed
profiles, and a provider account with a pending application. The doctor acts in `/provider`; the
patients act in `/patient` or through their own API sessions; every outcome is checked from both
sides. Requires the Phase 4D API (appointment responses with `providerId` and `rescheduleRequestedBy`).
Run with `--workers=1`.

### Provider sign-up and onboarding (`provider-onboarding.live.spec.ts`)

Same admin variables plus a disposable database (`MEDNET_LIVE_H2_*` or `MEDNET_LIVE_PG_*`); only the first
test runs without one. Each viewport project registers its own applicant through `/sign-up/provider`,
verifies by the stand-in link, signs in to onboarding, submits professional details, and is then
rejected, resubmits, approved and suspended by the administrator through the real API. After every
decision it checks that a copy of the applicant's previous session cookie is refused (`401`) and that
the next sign-in carries the new role. Provider-only endpoints (`/providers/me/patients`,
`/providers/me/lab-review-queue`, availability publishing, another patient's records and vitals) are
probed with the applicant's own cookies at every stage. Decision notices are checked to reach only the
applicant, and the last test reads `notification_email_outbox` to confirm the queued emails are addressed
only to the applicant and none reached `SENT`. Start the API with `EKDSEND_API_KEY=` (blank) so no email
can leave the machine. Run with `--workers=1`.

### Administration (`admin.live.spec.ts`)

Same admin variables. Signs the configured super admin in afresh (regression for the Phase 4E fix),
creates an `ADMIN`, a patient with a profile and a home-care staff account through the real endpoints,
then drives both administrative workspaces: provider intake and approval, the full account lifecycle,
protected deletion, the administrator's limits, server refusals for every `SUPER_ADMIN`-only endpoint,
home-care request assignment seen from the partner's queue, and sign-out. Run with `--workers=1`.

### Laboratory workflow (`laboratory.live.spec.ts`)

Same admin variables. Seeds a doctor with a confirmed appointment and the patient's record consent, an
`ADMIN`, two laboratory accounts and a home-care account. Then the patient requests a test in the patient
workspace, the administrator assigns it in the task queue, the laboratory starts it and submits a result,
the doctor returns it, the laboratory submits a correction, the doctor releases it and the patient reads
it. The unreleased result is checked invisible to the patient after every stage, and other accounts are
refused. Synthetic result text only. Run with `--workers=1`.

### Preview workspaces (`preview.spec.ts`, mocked)

`/preview/*` needs no API. The spec fails if any preview page makes an `/api/` request. In a production
build these routes return 404 unless the server runs with `MEDNET_PREVIEW_WORKSPACES=enabled`.
