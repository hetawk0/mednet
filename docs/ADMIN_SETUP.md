# MedNet Admin Setup

## Initial administrator

The first admin release uses one operator-provisioned administrator account. It is not possible to register an administrator from the public website.

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
| `GOOGLE_OAUTH_ENABLED` | Set `true` after Google credentials and PostgreSQL are configured |
| `GOOGLE_CLIENT_ID` | Google OAuth web client ID; server-side only |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret; store as a secret |
| `GOOGLE_REDIRECT_URI` | OAuth callback URI registered in Google Cloud |

Do not put production credentials in Git, `.lpad/manifest.json`, or public frontend variables. Use a long, unique password and rotate it by changing the LPAD secret and restarting the API service. The committed `.env.example` contains placeholders only, never usable credentials.

The API hashes the configured password with BCrypt at startup. It uses an HTTP-only session cookie and CSRF protection for login and logout. The admin routes return `401` until valid credentials are configured. Flyway creates the admin workflow tables when the PostgreSQL datasource is present.

## Google sign-in

Google sign-in is implemented by Spring Security OAuth2, not NextAuth. The `NEXTAUTH_URL` and `NEXTAUTH_SECRET` variables are not used by this app and should not be treated as enabling Google login.

Create a Google OAuth client with application type **Web application**. Add these authorized redirect URIs:

- Local: `http://localhost:8080/api/v1/auth/oauth2/callback/google`
- Production: `https://mednet.lpad.ekddigital.com/api/v1/auth/oauth2/callback/google`

Set `GOOGLE_OAUTH_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and the environment-specific `GOOGLE_REDIRECT_URI` in `.env` locally or LPAD for production. The Google client secret stays on the Spring server; never prefix it with `NEXT_PUBLIC_` or expose it to the web client.

Only verified Google email addresses are accepted. The email matching `MEDNET_ADMIN_EMAIL` receives the administrator role; other new Google accounts default to patient access. A provider role is assigned only when an administrator has approved an application for that email. Google login also requires the PostgreSQL datasource because account identity is persisted there.

## Current scope

The `/admin` page is connected to the protected Spring Boot API. Provider application review, account registry status, admin-entered service-request records, and audit events are stored in PostgreSQL. Account status changes do not yet disable patient/provider sign-in, and service requests are not yet connected to patient submission or notifications. No patient clinical records are exposed.

These endpoints store only non-clinical references and statuses. Provider applications and service requests are currently entered by administrators; patient/provider registration and request submission are separate, not-yet-implemented flows. See the open admin and clinical-data questions in `req.md` before extending access to protected health information.
