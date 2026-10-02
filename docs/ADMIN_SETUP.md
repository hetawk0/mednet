# MedNet Admin Setup

## Initial administrator

The first admin release uses one operator-provisioned administrator account. It is not possible to register an administrator from the public website.

Set these values in the MedNet production environment in LPAD:

| Variable | Purpose |
| --- | --- |
| `MEDNET_ADMIN_EMAIL` | Email used to sign in at `/admin` |
| `MEDNET_ADMIN_PASSWORD` | Initial administrator password; store as a secret |
| `SESSION_COOKIE_SECURE` | Keep `true` behind HTTPS; set `false` only for local HTTP development |

Do not put production credentials in Git, `.lpad/manifest.json`, or public frontend variables. Use a long, unique password and rotate it by changing the LPAD secret and restarting the API service.

The API hashes the configured password with BCrypt at startup. It uses an HTTP-only session cookie and CSRF protection for login and logout. The admin routes return `401` until valid credentials are configured.

## Current scope

The `/admin` page is connected to the protected Spring Boot API and reports API status. Provider review, account support, service requests, and persistent administrative audit are not yet implemented; the page reports these honestly as disconnected. No patient clinical records are exposed.

The workflow APIs should be added only alongside their data models, role rules, audit persistence, and confirmed product requirements. See the open admin and clinical-data questions in `req.md`.