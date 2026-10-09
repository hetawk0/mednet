# Frontend API Handoff

This guide is for frontend developers integrating with the MedNet backend. Spring Boot is the
authoritative API and business-rule layer; the frontend must not replace its authorization,
validation, or clinical workflow rules.

## API reference

The OpenAPI specification is generated from the running Spring Boot application:

- Production Swagger UI: <https://mednet.lpad.ekddigital.com/swagger-ui/>
- Production OpenAPI JSON: <https://mednet.lpad.ekddigital.com/api-docs/>
- Local Swagger UI: <http://localhost:8080/swagger-ui>
- Local OpenAPI JSON: <http://localhost:8080/api-docs/>

The production reference is protected and requires a MedNet `ADMIN` or `SUPER_ADMIN` session.
Sign in through the MedNet web application using an account provisioned for development; do not
share administrator credentials. The API paths are routed to the Spring Boot service by the
Launchpad manifest. Production uses trailing-slash canonical paths; Swagger UI routes directly
to its index from `/swagger-ui/` to avoid a redirect loop with the hosting proxy.

The admin console's **User management** area supports account provisioning with a display name and
initial manual or generated password; generated passwords are shown once after creation. Public
account IDs use a short uppercase type prefix and a 12-character Crockford Base32 token:
`PT` (patient), `PR` (provider), `HC` (home-care staff), `LB` (laboratory staff), `AD` (admin),
and `SA` (super admin). IDs are immutable after creation, including when account type changes;
the account type field reflects current permissions. The UUID remains an internal database key
and is not shown in the admin interface. `GET /api/v1/admin/accounts` supports `page`, `size`
(maximum 100), `search` (display name, email, or public ID), `status`, `accountType`, and
`emailVerified` filters. Admins can
suspend or reactivate accounts. `SUPER_ADMIN` can edit name, email, account type, and password,
toggle verification with `PATCH /api/v1/admin/accounts/{id}/verification`, or delete an account.
New accounts are unverified by default; only verified accounts can sign in. Accounts linked to
care or clinical records cannot be deleted or have their account type changed, and the configured
administrator account cannot be modified or deleted.

The overview module statuses describe actual API/storage availability: database-backed workflows
are `CONNECTED` when PostgreSQL is configured, `NOT_CONFIGURED` otherwise. Text consultations are
implemented; video/voice consultations remain `DISABLED` and are not a PostgreSQL configuration
problem.

## Local development

Follow the root [README](../README.md) to configure PostgreSQL and start the API. Then start the
web app from `web/`. During local web development, Next.js proxies `/api/v1/*` to the API at
`http://localhost:8080`; set `MEDNET_API_URL` if the API is listening elsewhere.

The frontend should call the same-origin `/api/v1/...` paths. This keeps browser requests on the
web origin and lets the development proxy or production reverse proxy route them to Spring Boot.
Use the session cookie for authentication; do not persist session credentials in browser storage.
For state-changing requests, first obtain the CSRF token from `GET /api/v1/auth/csrf`, then send
the returned token using the returned header name.

## Implemented API areas

The generated OpenAPI reference is the source of truth for exact paths, request and response
schemas, status codes, and parameters. The current guarded MVP includes:

- Account registration, sign-in, session, email verification, password recovery, and Google sign-in status.
- Patient profile, consent-gated records, medications, vitals, and notifications.
- Provider applications, approved-provider directory, availability, and appointments.
- Participant-only text conversations associated with confirmed appointments.
- Patient home-care and laboratory requests, assigned partner work queues, and laboratory result review/release.
- Administrator dashboard, provider review, account provisioning, and operational workflows.

Provider availability slots expose `consultationMode` as `IN_PERSON` or `TEXT`; omit it only for
legacy compatibility, where the backend defaults it to `TEXT`. Appointment responses include the
selected slot's mode. Only confirmed `TEXT` appointments can open a text consultation.

## Important scope boundaries

The backend is a guarded MVP, not a completed clinical/service launch. Video or voice consultations,
emergency handling, payments, binary file uploads, external service routing, SMS/email/push
notifications, prescribing, medication interaction checks, and vital thresholds/alerts are not
implemented. Some current safeguards are conservative engineering defaults that still need sponsor
and legal confirmation.

Before extending those areas, review the blocking and high-priority product decisions in
[req.md](req.md), especially release scope, provider credential verification, payments,
consultation modes, emergency handling, notification channels, supported platforms, and Liberian
regulatory requirements. The [Backend/API Plan](backend-api-plan.md) describes the wider design and
the [Admin Setup](ADMIN_SETUP.md) documents privileged operational APIs.
