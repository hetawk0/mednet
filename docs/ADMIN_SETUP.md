# MedNet Admin Setup

## Initial SUPER_ADMIN

The operator-provisioned `MEDNET_ADMIN_EMAIL` account is the initial `SUPER_ADMIN`. It can access the administration console, change account roles, suspend or reactivate accounts, and delete account records. Public registration can create only `PATIENT` or `PROVIDER` accounts; it can never create an administrator role.

For local development, copy the root `.env.example` to `.env` and set local-only values there. Spring Boot imports that file automatically; `.env` is ignored by Git.

For production, set these values in the MedNet environment in LPAD:

| Variable                     | Purpose                                                                 |
| ---------------------------- | ----------------------------------------------------------------------- |
| `MEDNET_ADMIN_EMAIL`         | Email used to sign in at `/admin`                                       |
| `MEDNET_ADMIN_PASSWORD`      | Initial administrator password; store as a secret                       |
| `SESSION_COOKIE_SECURE`      | Keep `true` behind HTTPS; set `false` only for local HTTP development   |
| `SPRING_DATASOURCE_URL`      | PostgreSQL JDBC URL, for example `jdbc:postgresql://<host>:5432/mednet` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL application user                                             |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password; store as a secret                                  |
| `GOOGLE_OAUTH_ENABLED`       | Legacy compatibility flag; credentials automatically enable Google      |
| `GOOGLE_CLIENT_ID`           | Google OAuth web client ID; server-side only                            |
| `GOOGLE_CLIENT_SECRET`       | Google OAuth client secret; store as a secret                           |
| `GOOGLE_REDIRECT_URI`        | OAuth callback URI registered in Google Cloud                           |
| `EKDSEND_API_URL`            | EKDSend API base URL; defaults to `https://es.ekddigital.com/api/v1`   |
| `EKDSEND_API_KEY`            | Server-side EKDSend API key; store as a secret                          |
| `FROM_EMAIL`                 | Verified sender address; defaults to `support@ekddigital.com`            |

Do not put production credentials in Git, `.lpad/manifest.json`, or public frontend variables. Use a long, unique password and rotate it by changing the LPAD secret and restarting the API service. The committed `.env.example` contains placeholders only, never usable credentials.

The API hashes the configured password with BCrypt at startup and authenticates the configured `SUPER_ADMIN` without querying the database during login. When PostgreSQL is configured, startup also creates or corrects the matching account row to `SUPER_ADMIN`, active, and email-verified. The configured password remains the login credential; the account row's password is not used for this configured administrator. The configured administrator account cannot be suspended, demoted, or deleted through the account APIs. The API uses an HTTP-only session cookie and CSRF protection for login and logout. The admin routes return `401` until valid credentials are configured. Flyway creates the admin workflow tables when the PostgreSQL datasource is present.

## Account email delivery

MedNet uses the server-side EKDSend API for account verification, resend-verification, forgot-password, and password-reset messages. Configure `EKDSEND_API_URL`, `EKDSEND_API_KEY`, and a verified sender address in `FROM_EMAIL` in LPAD or the local `.env`; keep the API key server-only. MedNet sends the `type`, `to`, `from`, `subject`, `body`, and `text` fields (plus `html` for compatibility) and authenticates with the API key headers used by the other EKD projects. A verification request is considered queued only when EKDSend returns `success: true` or its documented queued-response shape with a message ID and accepted status. The API logs the returned message ID and status, never credentials, recipient addresses, or message content. An unconfirmed HTTP-success response or provider error returns a service-unavailable error instead of claiming the email was queued.

Registration and resend intentionally return privacy-preserving responses. The verification email is sent only for an account that exists and is not yet verified; unknown or already verified addresses do not trigger email. Verification is completed through a link, not a numeric code. If the API confirms that EKDSend queued a message but it never reaches the inbox, inspect the message ID/status in EKDSend delivery logs and verify that `FROM_EMAIL` is an approved sender for the configured EKDSend account; check spam and provider suppression/bounce details as well. A queued status confirms acceptance by EKDSend, not final inbox delivery. Password recovery sends a six-digit code that expires after 10 minutes; five invalid attempts invalidate it. The password form is unlocked only after server verification, and the resulting reset ticket is single-use.

## Google sign-in

Google sign-in is implemented by Spring Security OAuth2, not NextAuth. The `NEXTAUTH_URL` and `NEXTAUTH_SECRET` variables are not used by this app and should not be treated as enabling Google login.

Create a Google OAuth client with application type **Web application**. Add these authorized redirect URIs:

- Local: `http://localhost:8080/api/v1/auth/oauth2/callback/google`
- Production: `https://mednet.lpad.ekddigital.com/api/v1/auth/oauth2/callback/google`

Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and the environment-specific `GOOGLE_REDIRECT_URI` in `.env` locally or LPAD for production. Google is automatically enabled when the client credentials are present. The Google client secret stays on the Spring server; never prefix it with `NEXT_PUBLIC_` or expose it to the web client.

The API validates Google's OpenID ID token using Google's published signing-key endpoint (`https://www.googleapis.com/oauth2/v3/certs`). Keep that JWK Set URI configured on the Google client registration; without it, Google returns to `/sign-in?error=google` and the API logs `missing_signature_verifier`.

Only verified Google email addresses are accepted. The email matching `MEDNET_ADMIN_EMAIL` receives the `SUPER_ADMIN` role; other new Google accounts default to patient access. A provider role is assigned only when an administrator has approved an application for that email. Existing `ADMIN` and `SUPER_ADMIN` account roles remain in force at Google sign-in. The OIDC callback links the verified identity to the MedNet account, persists the MedNet role, and sends patients to `/patient`; that dashboard shows appointment and service-request counts only, not clinical details. Google login also requires the PostgreSQL datasource because account identity is persisted there.

If Google returns to `/sign-in?error=google`, the page shows a generic recovery message while the API logs a provider error code or a MedNet account-policy rejection. Confirm the registered redirect URI above and inspect the API logs; never include client secrets or tokens in support reports.

## Current scope

The `/admin` page is a protected operations console, not a login form. Authentication starts at `/sign-in`; the console requires `ADMIN` or `SUPER_ADMIN`, while profile/email/password/account-type edits, verification controls, role changes, and account deletion require `SUPER_ADMIN`. Account listing supports pagination (25/50/100), search by display name, email, or UUID, and status, account-type, and verification filters. Account IDs are stable UUIDs. Account creation requires a display name and an initial password; the UI may generate a secure password and reveals it only once. New accounts are unverified by default. A `SUPER_ADMIN` can explicitly mark an account verified or pending; email changes automatically return it to pending. The configured administrator cannot be changed or deleted. Accounts linked to clinical/care data cannot be deleted or have their account type changed. Provider application review, service-request summaries, and audit events are stored in PostgreSQL. Administrators may provision `HOME_CARE` and `LABORATORY` accounts, assign matching open requests to verified staff, and track assignment status. New provider applications and service requests generate in-app notifications for administrators; applicants are notified of approval or rejection, and assigned partner staff are notified of assignments. The admin queue exposes only request IDs, statuses and opaque staff account IDs, never patient request descriptions, locations, laboratory details, or clinical records. Assigned staff see only their own requests and the minimum service/location details needed to act.

The dashboard's workflow-connectivity statuses reflect whether the API module and its database-backed workflow are available, not a registry-only feature tier. Account support, service requests, home care, laboratory, and text consultations are connected when PostgreSQL is configured. Video/voice consultations are explicitly disabled and do not require PostgreSQL configuration.

Administrators can page through appointment scheduling summaries at `GET /api/v1/admin/appointments`. Responses include opaque participant IDs, appointment ID, time, and status only; patient names, provider names, and clinical notes are excluded. Access is recorded in the audit trail.

Home-care requests assigned to staff can be scheduled for a future time, then moved from open to in progress to resolved. Laboratory requests can be assigned and moved to in progress; assigned laboratory staff may submit a text-only result, but an approved provider with the patient's active consent and care relationship must release it before it is visible to the patient. Releasing a result resolves the request and appends the reviewed result to the patient's record. External service routing and binary result uploads remain disabled. The review gate is a conservative implementation safeguard and requires sponsor/legal validation before production. The legacy `/api/v1/admin/requests` endpoint remains an administrator-entered, non-clinical reference workflow. See the open admin and clinical-data questions before extending access to protected health information.

To provision partner staff, create an account through `POST /api/v1/admin/accounts` with `displayName`, `email`, `accountType` (`HOME_CARE` or `LABORATORY`), and a 12–72 character `password`. New accounts are pending verification and cannot sign in until verified; a `SUPER_ADMIN` can change this through `PATCH /api/v1/admin/accounts/{id}/verification` with `{"emailVerified":true}`. Assign an open request with `PATCH /api/v1/admin/service-requests/{id}/assignment` and `{"staffAccountId":"<account-id>"}`. Partner staff access their assigned queue at `GET /api/v1/partner/service-requests?type=HOME_CARE` or `type=LABORATORY`; the service role must match the type. Lab users can start work but cannot release or complete a request.
