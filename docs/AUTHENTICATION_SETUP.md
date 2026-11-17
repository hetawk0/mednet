# MedNet Authentication Setup

MedNet uses its Spring Boot API for account registration, password sign-in, session management,
email verification, and password recovery. The API stores account credentials and issues
HTTP-only session cookies protected by CSRF tokens. Google OAuth is also supported when its client
credentials are configured. Google identities can link only to an existing MedNet account with the
same Google-verified email; Google sign-in does not silently create accounts.

## Runtime configuration

Set these values in the MedNet API environment. Never put secrets in Git or frontend variables.

| Variable | Purpose |
| --- | --- |
| `MEDNET_ADMIN_EMAIL` | Initial administrator account email |
| `MEDNET_ADMIN_PASSWORD` | Initial administrator password; keep server-side |
| `MEDNET_PUBLIC_URL` | Public MedNet origin used to build verification and reset links |
| `GOOGLE_CLIENT_ID` | Google OAuth web client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth web client secret; keep server-side |
| `GOOGLE_REDIRECT_URI` | Optional explicit callback URI; otherwise derived from the public request URL |
| `EKDSEND_API_URL` | EKDSend API base URL; defaults to `https://es.ekddigital.com/api/v1` |
| `EKDSEND_API_KEY` | Server-only key used to send authentication emails |
| `FROM_EMAIL` | Approved sender address; defaults to `support@ekddigital.com` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL application user |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL application password; keep server-side |

The root `.env.example` contains placeholders suitable for local development. Production values
belong in the API service's protected deployment environment.

## Google OAuth

Create a Google OAuth web client and register these authorized redirect URIs:

- Production: `https://mednet.lpad.ekddigital.com/login/oauth2/code/google`
- Local development: `http://localhost:3000/login/oauth2/code/google`

Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the API environment. The Google button is
shown when the API has both credentials and a datasource configured. The API retains the Google
subject on the matching MedNet account, requires Google's `email_verified` claim, rejects
suspended accounts and identity/email mismatches, and uses MedNet's account role for authorization.
If an existing account has not verified its email, a verified Google sign-in confirms it; an
unverified local password is cleared so its owner can set a password through recovery.

If the Google-verified email matches `MEDNET_ADMIN_EMAIL`, the configured administrator receives
`SUPER_ADMIN`. Protect that Google account with strong MFA and secure the OAuth client secret.

## Account and email flow

1. The browser calls the MedNet API to register or request a verification/password-reset message.
2. The API generates a short-lived six-digit code and a separate single-use fallback link token.
3. The API sends the branded message directly to EKDSend using `EKDSEND_API_KEY`; no external
   identity provider generates codes or sends authentication messages.
4. The user enters the code in MedNet, or follows the fallback link. Password recovery verifies
   the code and issues a short-lived reset ticket before accepting a new password.

Registration and resend use privacy-preserving responses. EKDSend accepting or queueing a message
does not prove final inbox delivery. If a message is missing, inspect the specific recipient's
delivery, bounce, suppression, or defer events in EKDSend and the receiving mail provider; also
check spam/quarantine and confirm that `FROM_EMAIL` is approved. MedNet logs provider status and
message identifiers for correlation, not recipients or message contents.

## Sessions and account access

The web client authenticates using same-origin `/api/v1/...` requests and an HTTP-only MedNet
session cookie. It does not store session credentials in browser storage. State-changing requests
obtain a CSRF token from `GET /api/v1/auth/csrf` and send it with the header named in the response.
Account roles and statuses come from the MedNet database and are never accepted from browser input.

`GET /api/v1/auth/session` returns `{ email, role, accountType }`. `role` is the effective role the
API enforces; `accountType` is the stored account type (null without an account record). Registration
accepts an optional `accountType` of `PATIENT` (the default) or `PROVIDER`. A `PROVIDER` account signs
in with the `PATIENT` role until an administrator approves its provider application, so the web
client uses `accountType` only to route such applicants to provider onboarding (`/provider`), never as
proof of approval. Keeping applicants out of the patient workspace is a client navigation choice
(decided 2026-11-17), not a server-side denial: patient endpoints still accept the `PATIENT` role.
Any change to a provider application's status (approve, reject, suspend) ends that
account's sessions, so the new role applies from the next sign-in.

Email/password sign-in requires a corresponding MedNet account and a local password. Google sign-in
can be used to link an existing account with the same verified Google email. Existing external
identity data is not migrated automatically.
