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
suspend or reactivate non-administrator accounts (an `ADMIN` gets 403 for `ADMIN` or `SUPER_ADMIN` targets; a
`SUPER_ADMIN` can change any account except the configured administrator). The admin service-request queue includes
`assignedStaffPublicId` (null until assigned). `SUPER_ADMIN` can edit name, email, account type, and password,
toggle verification with `PATCH /api/v1/admin/accounts/{id}/verification`, or delete an account.
New accounts are unverified by default; only verified accounts can sign in. Accounts linked to
care or clinical records cannot be deleted or have their account type changed, and the configured
administrator account cannot be modified or deleted.

The overview module statuses describe actual API/storage availability: database-backed workflows
are `CONNECTED` when PostgreSQL is configured, `NOT_CONFIGURED` otherwise. Text consultations are
implemented; video/voice consultations remain `DISABLED` and are not a PostgreSQL configuration
problem.

## Local development

Follow the root [README](../README.md) and [PostgreSQL setup](POSTGRESQL_SETUP.md) to configure
PostgreSQL and start the API. Phase 5A made no API contract change. All six live-API browser suites and the
backend workflow suite pass against PostgreSQL 17.6 (see `FRONTEND_PROGRESS.md`, Phase 5A). Against a
distant database, writes such as appointment requests can take several seconds; keep loading states.
Phase 5B (security): every endpoint not listed as public now answers `401` to signed-out callers
before validation (public: health, the auth entry points, `GET /providers`,
`GET /providers/{id}/availability`). Signing up again with the address of an existing unverified
account resends the verification email and keeps that account's password. Web pages now send
`X-Frame-Options: DENY` and related headers, so MedNet cannot be embedded in a frame. See
`SECURITY_AUDIT.md`.
Phase 5D (performance; additive, backward compatible — every list keeps its old response without `page`):
- `GET /providers` entries add `nextAvailableAt` (earliest open future slot or null); new public
  `GET /providers/{id}` (approved only, else 404) and `GET /providers/specialties` (`[{specialty, providers}]`).
- `GET /providers/me/lab-review-queue?page=&size=` (PROVIDER): `{content: [{patientAccountId, patientName,
  recordAccess, pendingResults, oldestPendingAt}], totalElements, totalPages, number, size,
  totalPendingResults, patientsWithoutRecordAccess}`; patients with results awaiting this provider's review
  (consent granted) first, then care patients without record access. No result text; open results per
  patient with `GET /patients/{id}/lab-results/pending` as before.
- With `page` (and `size` ≤ 100): `GET /admin/providers` (+ `status`, `search`), `/admin/requests`,
  `/admin/audit` (+ `search`, `resourceType`, repeatable `labelAction`), `/admin/service-requests`
  (+ `type`, repeatable `status`), `/partner/lab-results` (+ `status`), `/patients/me/lab-results` return a
  Spring page instead of the first 100 rows. `GET /admin/audit/resource-types` lists record types.
- `GET /partner/service-requests?type=LABORATORY` rows add `latestResult` (`{id, status, createdAt,
  reviewedAt}` or null; no text).

Appointment and provider-patient history is paged server-side (zero-based `page`, default `size=25`,
maximum `size=100`). `GET /appointments` continues to return only the authenticated patient's or
approved provider's appointments, with deterministic creation-time/ID ordering. Patient and provider
appointment views navigate every page while retaining their existing status groupings and actions.
The provider patient directory uses `GET /providers/me/patients?page=&size=` instead of deriving
patients from one appointment page. It returns only active patient accounts with an eligible
`CONFIRMED`, `COMPLETED`, or `RESCHEDULE_REQUESTED` care relationship to the authenticated approved
provider, ordered by patient name and account ID. The response contains only patient account ID and
profile name; it does not grant record access. `GET /providers/me/patients/{patientAccountId}` applies
the same relationship check for a detail page, and
`GET /providers/me/patients/{patientAccountId}/appointments?page=&size=` returns that relationship's
appointment history, still scoped to the authenticated provider. All three routes are provider-only;
the nested history route validates pagination and care access on the server.

Admin CSV exports are frontend-only and use the existing paged admin endpoints above; no API route or
authorization rule was added. Audit export reuses the current search and record-type filters, while
home-care/laboratory request exports reuse the selected request type and status filter. Appointment
and operational-reference exports page through their existing lists. Each page request is limited
to 100 rows, and every matching page is fetched before the download begins. The CSV allowlists only
fields already shown in the corresponding admin list: audit time/action/actor/short record reference,
appointment timing/mode/status/short references, request status/date/assigned public ID, or
operational reference/type/status/date. It excludes clinical text, patient records, lab result
summaries, medication/vital data, and hidden requester details. Cells are RFC 4180 quoted and
spreadsheet formula-leading values are neutralized. These list exports do not implement RPT-08
reporting data or add aggregate metrics.

Phase 5C (home care): no endpoint or contract change. The web client now uses the existing partner
endpoints for `HOME_CARE` accounts at `/home-care`: `GET /partner/service-requests?type=HOME_CARE`
(own assignments: `requestedService`, `locationDescription`, `status`, `scheduledAt`, dates; no patient
identity), `PATCH /partner/service-requests/{id}/schedule` `{scheduledAt}` (once, future, OPEN and
unscheduled; `409` otherwise, `400` for a past time) and `PATCH /partner/service-requests/{id}/status`
`{status: IN_PROGRESS | RESOLVED}` (OPEN with a visit time → IN_PROGRESS → RESOLVED; `409` otherwise,
`404` for a request not assigned to the caller). Sign-in now lands `HOME_CARE` on `/home-care`
(was `/account`), and `/account` forwards home-care staff there.
Phase 5B closure: authentication and email endpoints can answer `429` with `Retry-After` (seconds) and
error code `RATE_LIMITED`; show the message and let the user retry later. A session can now end at any
request (12 hours after sign-in, or when an administrator changes the account); the API answers the usual
`401`, which the workspaces already turn into a sign-in prompt. Then start the
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

- Account registration, sign-in, session, email verification, and password recovery. The API redirects
  after sign-in by role: `ADMIN`/`SUPER_ADMIN` → `/admin`, `PATIENT` → `/patient`,
  `PROVIDER` (approved application) → `/provider`, `LABORATORY` → `/laboratory`, any other role → `/account`.
- Laboratory staff read back their own submitted results with `GET /api/v1/partner/lab-results` (`LABORATORY` only;
  results they authored on requests still assigned to them, newest first, latest 100). Fields: `id`, `requestId`,
  `summary`, `status` (`PENDING_REVIEW`, `RETURNED`, `RELEASED`), `createdAt`, `reviewedAt`. No patient or reviewer
  identifier. Read-only: review decisions remain with the doctor.
- Patient profile, consent-gated records, medications, vitals, and notifications.
- Patient home-care/laboratory request histories accept `page` and optional `size` (default 25, maximum 100) and return a Spring `Page`; omitting `page` preserves the existing list response. Both forms accept optional `status` (`OPEN`, `IN_PROGRESS`, `RESOLVED`, or `CANCELLED`) and `search`. Search is trimmed, case-insensitive, limited to 120 characters, and matches only the requested service description or location visible to the patient.
- Assigned partner work queues also accept `page` and `size` (default 25, maximum 100), with optional `status` and `search`; laboratory pages include the latest result status without exposing result text. Results are restricted to the authenticated staff account's assignments of the requested `type` (`HOME_CARE` or `LABORATORY`); search matches only requested service descriptions and locations already visible to that staff role. Omitting `page` preserves the existing list response.
- For these service-request endpoints, invalid statuses and searches longer than 120 characters return `400`. Medication schedule and dose histories remain paginated as documented below.
- `GET /api/v1/conversations?page=&size=` and `GET /api/v1/patients/me/record-consents?page=&size=` return paginated histories (default size 25, maximum 100). Omitting `page` preserves the existing list responses; conversation pages include only current eligible care relationships.
- Provider pending laboratory results for a patient accept optional `page` and `size` (default 25, maximum 100); the existing unpaged response shape remains available. Record-consent and care-relationship checks apply to both forms.
- Provider applications, approved-provider directory, availability, and appointments.
- In-app appointment reminders for both participants, generated within 24 hours and again within 1 hour of a confirmed appointment. These reminders are idempotent; SMS, email, and push delivery remain out of scope.
- Participant-only text conversations associated with confirmed appointments. Providers can record one text consultation outcome with `PUT /api/v1/consultations/{id}/outcome` after ending the session; the backend appends an `ENCOUNTER_SUMMARY` only when the existing patient consent and care-relationship checks pass. The end-session endpoint remains backward compatible.
- Patient home-care and laboratory requests, assigned partner work queues, and laboratory result review/release.
- Administrator dashboard, provider review, account provisioning, and operational workflows.
- Notification preferences are available to signed-in users at `GET /api/v1/notifications/preferences`
  and can be changed with `PATCH /api/v1/notifications/preferences` using
  `{"emailEnabled":true|false}`. Email notifications are queued transactionally with the in-app
  notification, sent only to active accounts with verified email addresses and email enabled, and
  retried from a persisted outbox with bounded exponential backoff. Appointment emails contain only
  the current appointment status and scheduled time; other notification emails are generic and do
  not include message previews, laboratory result text, medication details, or clinical content.
  Users can update this preference in their account profile. Delivery uses the existing EKDSend
  configuration; email can be disabled globally with `MEDNET_EMAIL_NOTIFICATIONS_ENABLED=false`.

Provider availability slots expose `consultationMode` as `IN_PERSON` or `TEXT`; omit it only for
legacy compatibility, where the backend defaults it to `TEXT`. Appointment responses include the
selected slot's mode. Only confirmed `TEXT` appointments can open a text consultation.

Appointment responses (`GET/POST/PATCH /api/v1/appointments…`) also include `providerId` (the approved
provider record id used by `/providers/{id}/availability`, consent and conversations), `providerSpecialty`, and
`rescheduleRequestedBy` (`PATIENT` or `PROVIDER` while the status is `RESCHEDULE_REQUESTED`, otherwise `null`).
These fields were added in Phase 4D; they are additive and derived from existing data.

## Important scope boundaries

The backend is a guarded MVP, not a completed clinical/service launch. Video or voice consultations,
emergency handling, payments, binary file uploads, external service routing, SMS/push notifications,
prescribing, medication interaction checks, and vital thresholds/alerts are not implemented. Email
delivery is available for notifications under the privacy boundaries above. Some current safeguards
are conservative engineering defaults that still need sponsor and legal confirmation.

Before extending those areas, review the blocking and high-priority product decisions in
[req.md](req.md), especially release scope, provider credential verification, payments,
consultation modes, emergency handling, notification channels, supported platforms, and Liberian
regulatory requirements. The [Backend/API Plan](backend-api-plan.md) describes the wider design and
the [Admin Setup](ADMIN_SETUP.md) documents privileged operational APIs.
