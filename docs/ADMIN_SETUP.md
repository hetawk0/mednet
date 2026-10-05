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

Do not put production credentials in Git, `.lpad/manifest.json`, or public frontend variables. Use a long, unique password and rotate it by changing the LPAD secret and restarting the API service. The committed `.env.example` contains placeholders only, never usable credentials.

The API hashes the configured password with BCrypt at startup. It uses an HTTP-only session cookie and CSRF protection for login and logout. The admin routes return `401` until valid credentials are configured. Flyway creates the admin workflow tables when the PostgreSQL datasource is present.

## Account email delivery

MedNet uses the server-side EKDSend API for account verification, resend-verification, forgot-password, and password-reset messages. Configure `EKDSEND_API_URL`, `EKDSEND_API_KEY`, and `FROM_EMAIL` in LPAD or the local `.env`; keep the API key server-only. Password recovery sends a six-digit code that expires after 10 minutes; five invalid attempts invalidate it. The password form is unlocked only after server verification, and the resulting reset ticket is single-use.

## Google sign-in

Google sign-in is implemented by Spring Security OAuth2, not NextAuth. The `NEXTAUTH_URL` and `NEXTAUTH_SECRET` variables are not used by this app and should not be treated as enabling Google login.

Create a Google OAuth client with application type **Web application**. Add these authorized redirect URIs:

- Local: `http://localhost:8080/api/v1/auth/oauth2/callback/google`
- Production: `https://mednet.lpad.ekddigital.com/api/v1/auth/oauth2/callback/google`

Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and the environment-specific `GOOGLE_REDIRECT_URI` in `.env` locally or LPAD for production. Google is automatically enabled when the client credentials are present. The Google client secret stays on the Spring server; never prefix it with `NEXT_PUBLIC_` or expose it to the web client.

Only verified Google email addresses are accepted. The email matching `MEDNET_ADMIN_EMAIL` receives the `SUPER_ADMIN` role; other new Google accounts default to patient access. A provider role is assigned only when an administrator has approved an application for that email. Existing `ADMIN` and `SUPER_ADMIN` account roles remain in force at Google sign-in. Google login also requires the PostgreSQL datasource because account identity is persisted there.

If Google returns to `/sign-in?error=google`, the page shows a generic recovery message while the API logs a provider error code or a MedNet account-policy rejection. Confirm the registered redirect URI above and inspect the API logs; never include client secrets or tokens in support reports.

## Current scope

The `/admin` page is a protected operations console, not a login form. Authentication starts at `/sign-in`; the console requires `ADMIN` or `SUPER_ADMIN`, while role changes and account deletion require `SUPER_ADMIN`. The account registry supports pagination, email search, status filtering, verification state, role changes, suspension/reactivation, and deletion. Provider application review, service-request records, and audit events are stored in PostgreSQL. No patient clinical records are exposed.

These endpoints store only non-clinical references and statuses. Provider applications and service requests are currently entered by administrators; patient/provider registration and request submission are separate, not-yet-implemented flows. See the open admin and clinical-data questions in `req.md` before extending access to protected health information.
