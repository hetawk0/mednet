# MedNet Admin Setup

## Initial SUPER_ADMIN

The operator-provisioned `MEDNET_ADMIN_EMAIL` account is the initial `SUPER_ADMIN`. It can access the administration console, change account roles, suspend or reactivate accounts, and delete account records. Public registration can create only `PATIENT` accounts; it can never create an administrator role. MedNet manages account passwords and sessions locally, and sends verification and password-recovery messages through EKDSend. See [Authentication Setup](AUTHENTICATION_SETUP.md).

For local development, copy the root `.env.example` to `.env` and set local-only values there. Spring Boot imports that file automatically; `.env` is ignored by Git.

For production, set these values in the MedNet environment in LPAD:

| Variable | Purpose |
| --- | --- |
| `MEDNET_ADMIN_EMAIL` | Initial administrator email; receives `SUPER_ADMIN` |
| `MEDNET_ADMIN_PASSWORD` | Initial administrator password; store as a server-side secret |
| `MEDNET_PUBLIC_URL` | Public MedNet origin used in verification and password-reset links |
| `GOOGLE_CLIENT_ID` | Google OAuth web client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth web client secret; store as a secret |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL, for example `jdbc:postgresql://<host>:5432/mednet` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL application user |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password; store as a secret |
| `EKDSEND_API_URL` | EKDSend API base URL; defaults to `https://es.ekddigital.com/api/v1` |
| `EKDSEND_API_KEY` | Server-only EKDSend API key used by the MedNet API for authentication mail; store as a secret |
| `FROM_EMAIL` | Verified sender address; defaults to `support@ekddigital.com` |
| `MEDNET_EMAIL_NOTIFICATIONS_ENABLED` | Enable email delivery for in-app notification events; defaults to `true` |
| `MEDNET_EMAIL_DISPATCH_INTERVAL` | Interval for persisted notification-email outbox dispatch; ISO-8601 duration, defaults to `PT15S` |
| `MEDNET_EMAIL_DISPATCH_INITIAL_DELAY` | Delay before the first notification-email dispatch; defaults to `PT10S` |
| `MEDNET_WEB_BASE_URL` | MedNet web origin linked from account notification emails; defaults to `http://localhost:3000` |

Do not put production credentials in Git, `.lpad/manifest.json`, or public frontend variables. Use a long, unique password and rotate it by changing the LPAD secret and restarting the API service. The committed `.env.example` contains placeholders only, never usable credentials.

The browser authenticates through the MedNet API using a server-side session cookie and CSRF protection. The API resolves account roles and status from `platform_accounts`; it never trusts a role supplied by the browser. The configured administrator account cannot be suspended, demoted, or deleted through the account APIs.

## Database

Local PostgreSQL roles, databases and test commands are described in [PostgreSQL setup](POSTGRESQL_SETUP.md). The API connects with a dedicated, non-superuser application role that owns only the MedNet database; integration and browser tests use a separate `mednet_test` role and `_test` databases so test data cannot reach development or production data. In production, `SPRING_DATASOURCE_PASSWORD` is a secret like the administrator password. If the database rejects the login, API startup fails after Flyway's connection retries with PostgreSQL's message, which names the user but not the password. Phase 5A (2026-10-11): the local `mednet` role named in `.env` had never been created on the local PostgreSQL 18 server (server log: `Role "mednet" does not exist`); that, not a wrong password, is why the local credentials were rejected. Development now uses a Supabase project through its session-mode pooler (port 5432, `sslmode=require`); run the API close to the database's region, because each round trip costs about 200 ms from a distant machine.

## Security notes (Phase 5B)

See [Security audit](SECURITY_AUDIT.md). Set `MEDNET_TRUSTED_PROXIES` to the reverse proxy's address range so authentication rate limits use the actual client address. Session lifetime is configured by MedNet; the API checks account status on authenticated requests so suspension and role changes take effect without waiting for a token to expire.

## Account email delivery

The MedNet API generates its own single-use verification/recovery codes and secure fallback links, then sends them directly through EKDSend. It also delivers opted-in account notification emails from a persisted transactional outbox, retrying temporary failures with bounded backoff. Appointment notifications include only appointment status and UTC scheduled time; other notices contain no message previews or clinical details. Only active accounts with verified email addresses receive notifications, and users can disable email in their profile. Keep `EKDSEND_API_KEY` server-side in the MedNet API environment, never in the browser or Git.

Registration and resend use privacy-preserving responses. The six-digit code is the primary verification method; the secure link is an alternative. Password recovery uses a code and a short-lived reset ticket. For delivery failures, inspect EKDSend's recipient/message status and verify that `FROM_EMAIL` is an approved sender. A queued message confirms provider acceptance, not final inbox delivery.

The verification page includes a resend action that requests a fresh code and secure link through
the MedNet API. Google OAuth is optional; set both Google client credentials and register
`https://mednet.lpad.ekddigital.com/login/oauth2/code/google` in the Google Cloud OAuth client.
Google can link only an existing account with the same verified email. See
[Authentication Setup](AUTHENTICATION_SETUP.md) for the local callback and account-linking rules.

All grouped services in an LPAD project receive the project's configured environment variables during build and runtime. Keep secrets in the LPAD project environment, never in source code or client bundles. Treat services in one project as sharing the same secret access; use separate LPAD projects if services need different secret privileges.

## Current scope

The `/admin` area is a protected operations workspace, not a login form. A `SUPER_ADMIN` session opens the super-administrator workspace (overview, users and roles, providers, email verification, security/audit, read-only configuration status, task queue, appointments) and an `ADMIN` session opens the administrator workspace (overview, task queue, onboarding, appointments, escalations preview, reports); the API still authorizes every request. After sign-in, administrators land on `/admin`, patients on `/patient`, approved providers on `/provider` and laboratory staff on `/laboratory`. The configured administrator can sign in any number of times per API process (each lookup builds a fresh principal; before this fix the second sign-in failed). Authentication starts at `/sign-in`; the console requires `ADMIN` or `SUPER_ADMIN`, while profile/email/password/account-type edits, verification controls, role changes, and account deletion require `SUPER_ADMIN`. Account listing supports pagination (25/50/100), search by display name, email, or public ID, and status, account-type, and verification filters. Public IDs are short, uppercase type-prefixed identifiers: `PT` patient, `PR` provider, `HC` home-care staff, `LB` laboratory staff, `AD` admin, and `SA` super admin, followed by a 12-character Crockford Base32 token. They are generated once, remain immutable when account type changes, and are distinct from the internal UUID database key, which is not displayed in the admin table. Account creation requires a display name and an initial password; the UI may generate a secure password and reveals it only once. New accounts are unverified by default. A `SUPER_ADMIN` can explicitly mark an account verified or pending; email changes automatically return it to pending. The environment-configured administrator cannot be edited, suspended, demoted, or deleted through account management; change its login credentials using `MEDNET_ADMIN_EMAIL` and `MEDNET_ADMIN_PASSWORD` in deployment settings. For other accounts linked to clinical/care data, profile fields remain editable, but changing account type or deleting the account is blocked to preserve data ownership and authorization. The API reports the specific reason for a conflict to the admin UI. Provider application review, service-request summaries, and audit events are stored in PostgreSQL. Administrators may provision `HOME_CARE` and `LABORATORY` accounts, assign matching open requests to verified staff, and track assignment status. New provider applications and service requests generate in-app notifications for administrators; applicants are notified of approval or rejection, and assigned partner staff are notified of assignments. The admin queue (`GET /api/v1/admin/service-requests`) exposes only request IDs, types, statuses, creation times and, once assigned, the assigned staff member's public account ID (`assignedStaffPublicId`; null when unassigned), never patient request descriptions, locations, laboratory details, or clinical records. An `ADMIN` can suspend or reactivate patient, provider and partner accounts but not `ADMIN` or `SUPER_ADMIN` accounts (403); only a `SUPER_ADMIN` can change the status of administrator accounts. Assigned staff see only their own requests and the minimum service/location details needed to act.

The dashboard's workflow-connectivity statuses reflect whether the API module and its database-backed workflow are available, not a registry-only feature tier. Account support, service requests, home care, laboratory, and text consultations are connected when PostgreSQL is configured. Video/voice consultations are explicitly disabled and do not require PostgreSQL configuration.

Administrators can page through appointment scheduling summaries at `GET /api/v1/admin/appointments`. Responses include opaque participant IDs, appointment ID, time, and status only; patient names, provider names, and clinical notes are excluded. Access is recorded in the audit trail.

Home-care requests assigned to staff can be scheduled for a future time, then moved from open to in progress to resolved. Laboratory requests can be assigned and moved to in progress; assigned laboratory staff may submit a text-only result, but an approved provider with the patient's active consent and care relationship must release it before it is visible to the patient. Releasing a result resolves the request and appends the reviewed result to the patient's record. External service routing and binary result uploads remain disabled. The review gate is a conservative implementation safeguard and requires sponsor/legal validation before production. The legacy `/api/v1/admin/requests` endpoint remains an administrator-entered, non-clinical reference workflow. See the open admin and clinical-data questions before extending access to protected health information.

To provision partner staff, create an account through `POST /api/v1/admin/accounts` with `displayName`, `email`, `accountType` (`HOME_CARE` or `LABORATORY`), and a 12–72 character `password`. New accounts are pending verification and cannot sign in until verified; a `SUPER_ADMIN` can change this through `PATCH /api/v1/admin/accounts/{id}/verification` with `{"emailVerified":true}`. Assign an open request with `PATCH /api/v1/admin/service-requests/{id}/assignment` and `{"staffAccountId":"<account-id>"}`. Partner staff access their assigned queue at `GET /api/v1/partner/service-requests?type=HOME_CARE` or `type=LABORATORY`; the service role must match the type. Lab users can start work but cannot release or complete a request; they can read back the review status of their own submissions at `GET /api/v1/partner/lab-results`.
