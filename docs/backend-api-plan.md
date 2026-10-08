# MedNet Backend and API Plan

This document is the dedicated backend and API design plan for MedNet. It complements the product-level requirements in [req.md](req.md) and focuses only on the server architecture, API contract, security model, domain boundaries, and production quality standards. The cross-stack boundaries and deployment direction are consolidated in the [Production Architecture Guide](PRODUCTION_ARCHITECTURE_GUIDE.md).

---

## 1. Architectural direction

MedNet should start as a modular monolith in Java 21 with Spring Boot. This gives the project a strong production baseline without the operational complexity of a microservice system before the business is proven.

### Recommended stack

- Java 21 LTS
- Spring Boot 4.1.x or the latest supported patch compatible with the selected team tooling
- Spring Web MVC / REST
- Spring Security
- Spring Data JPA + Hibernate
- PostgreSQL
- Flyway for database migrations
- Spring Validation + Bean Validation
- OpenAPI / Swagger for API documentation
- JUnit 5 + Spring test support + Testcontainers
- Gradle with Kotlin DSL and the committed Gradle Wrapper is the supplied build-system recommendation. The current starter uses Maven; choose whether to retain or migrate it before substantial feature work, and do not maintain two authoritative builds.

### Why this is appropriate

- The product is a healthcare coordination platform, not a user-count-heavy consumer app at launch.
- Clinical workflows need consistent transactions and clear data ownership.
- A modular monolith keeps domains clear while staying easier to operate than distributed services.
- A single backend is easier to secure and audit for patient data.

---

## 2. Production structure

Use a clear layered backend structure with domain-driven package boundaries.

```text
com.mednet
├── MedNetApplication.java
├── api/
│   ├── controller/
│   ├── dto/
│   ├── mapper/
│   ├── validation/
│   └── docs/
├── app/
│   ├── service/
│   ├── facade/
│   ├── event/
│   └── policy/
├── domain/
│   ├── auth/
│   ├── patient/
│   ├── provider/
│   ├── appointment/
│   ├── message/
│   ├── record/
│   ├── medication/
│   ├── vital/
│   ├── homecare/
│   ├── laboratory/
│   ├── notification/
│   ├── admin/
│   └── common/
├── infra/
│   ├── config/
│   ├── security/
│   ├── repository/
│   ├── entity/
│   ├── persistence/
│   ├── email/
│   ├── sms/
│   ├── storage/
│   ├── audit/
│   └── integrations/
├── common/
│   ├── exception/
│   ├── util/
│   ├── enum/
│   ├── constant/
│   └── response/
└── shared/
    ├── clock/
    ├── id/
    ├── audit/
    └── event/
```

### Naming rules for backend

- Use full names for domain concepts when they are already readable: `patient`, `provider`, `record`, `appointment`.
- Use short names only when the term is repeated and noisy: `auth`, `appt`, `msg`, `med`, `notif`, `admin`, `dto`, `svc`, `repo`, `cfg`, `util`.
- Prefer `appointment` over `appt` in public-facing API contracts and domain model names.
- Prefer `notification` over `notif` in user-facing messages and external docs.
- Keep package names narrow and specific.

---

## 3. API design

### Base API rules

- API versioning under `/api/v1`
- Resource-oriented endpoints; nouns first, verbs last
- One response envelope pattern across all endpoints
- Structured validation and authorization errors
- Audit trail for clinical state changes
- No stack traces in production responses

The current API preserves its established success response bodies for frontend compatibility. Errors use a structured
`success: false` envelope with a stable code, details, and request ID; successful responses will move to the full
`success` / `data` / `meta` envelope as part of a coordinated API and client contract change.

### Response envelope

```json
{
  "success": true,
  "data": {
    "id": "p_123"
  },
  "meta": {
    "requestId": "req_456",
    "page": 1,
    "limit": 20,
    "total": 142
  }
}
```

### Error envelope

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [{ "field": "email", "message": "Email is required" }]
  }
}
```

### Core API groupings

| Group        | Example endpoints                                                             |
| ------------ | ----------------------------------------------------------------------------- |
| Auth         | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/forgot-password`, `/api/v1/auth/reset-password/verify`, `/api/v1/auth/reset-password` |
| Patient      | `/api/v1/patients/me/profile`, `/api/v1/patients/me/records`, `/api/v1/patients/{patientAccountId}/records`, `/api/v1/patients/me/record-consents` |
| Provider     | `/api/v1/providers` (approved directory), `/api/v1/providers/applications`, `/api/v1/providers/me/application`, `/api/v1/providers/me/availability`, `/api/v1/providers/{id}/availability` |
| Appointment  | `/api/v1/appointments`, `/api/v1/appointments/{id}` (patient requests remain pending until provider confirmation) |
| Message      | `/api/v1/conversations/{id}/messages`                                         |
| Medication   | `/api/v1/patients/{id}/medications`                                           |
| Vitals       | `/api/v1/patients/{id}/vitals`                                                |
| Home Care    | `/api/v1/home-care-requests`, `/api/v1/partner/service-requests/{id}/schedule` |
| Lab          | `/api/v1/lab-requests`, `/api/v1/patients/me/lab-results`, `/api/v1/patients/{id}/lab-results/pending`, `/api/v1/lab-results/{id}/review` |
| Partner work | `/api/v1/partner/service-requests` (assigned `HOME_CARE` / `LABORATORY` work) |
| Notification | `/api/v1/notifications`, `/api/v1/notifications/{id}/read`                    |
| Admin        | `/api/v1/admin/dashboard`, `/api/v1/admin/providers/pending`                  |

Availability is represented by explicit future instants (ISO-8601 timestamps), not recurring schedules. A pending or
confirmed appointment reserves its slot. Rescheduling requires the other participant's acceptance; cancellation is
allowed before the slot starts. The current contract intentionally has no attendance-mode, payment, cancellation-fee,
or no-show fields while their product decisions remain open.

### Medical records (current implementation)

- Patients can list their own clinical entries at `GET /api/v1/patients/me/records`.
- Providers can list and add entries at `/api/v1/patients/{patientAccountId}/records`. Access requires an active approved provider account, a confirmed/completed appointment relationship (including an in-progress reschedule), and active, explicit consent from that patient. Appointment responses include the participant's opaque patient account ID for this purpose.
- Patients grant consent with `POST /api/v1/patients/me/record-consents` (`providerId`), list grants with `GET`, and revoke immediately with `DELETE /api/v1/patients/me/record-consents/{providerId}`. Grants are provider-specific and remain active until revoked; access is still denied when the care relationship is no longer confirmed/completed.
- The supported categories are encounter summaries, diagnoses, prescriptions, allergies, medical history, and document references. Entries show their provider author and effective/recorded timestamps. Corrections are additional entries linked with `amendsRecordId`; there is no update or delete endpoint.
- Record reads and writes, and consent grants/revocations, create audit metadata without storing clinical summaries in the audit trail. Administrators cannot access clinical payloads through admin APIs. Account role changes/deletion are blocked when an account is linked to clinical records or patient consent.
- `DOCUMENT` currently represents a provider-authored document reference/summary only. Binary upload/download and patient-supplied historical files are not enabled: the deployment has no configured private object storage, malware scanning, or retention policy. Do not use public URLs or local application storage as a workaround; resolve OQ-25 and configure private storage before enabling file transfer.
- Patient-entered medication schedules and self-reported vitals are also appended to the record timeline as patient-authored entries. Provider-reviewed laboratory results are appended to the timeline when released. The implementation uses a small MedNet clinical-entry model, not a claim of full FHIR conformance. Clinical codes are optional references and are not validated against a terminology server. Consultation entries remain gated until their workflow is approved.

### Other candidate workflows (guarded MVP)

- Text-only conversations are available only between a patient and an approved provider with a confirmed, completed, or actively rescheduled appointment relationship. Conversation history is retained; attachments, emergency handling, service-level commitments, and messaging outside that care relationship remain disabled pending OQ-17–OQ-19 and OQ-49.
- Patients may enter free-text medication names and doses with one daily reminder time and an IANA time zone. Reminders are in-app only. This is not prescribing: provider-created schedules, drug catalogue lookup, interaction checking, and provider adherence reporting are disabled pending OQ-28–OQ-31.
- Patients may enter manually reported vital metrics, numeric values and units. The value is labeled patient-reported and added to the record timeline. Providers need the existing care relationship and explicit record consent to read them. The backend does not infer a clinical metric list, range, or alert threshold; resolve OQ-32–OQ-34 before clinical alerting or device integration.
- In-app notifications are available with generic, non-clinical titles. Appointment changes, messages, service-request changes, and medication reminders create notifications. SMS, email, push delivery, and notification preferences are not enabled pending OQ-43–OQ-45.
- Patients can submit and track home-care and laboratory requests, and cancel them while open. Administrators can see opaque request IDs and statuses and move unassigned requests into progress or cancel them; request details are not exposed in the admin API. External routing and binary lab-result upload remain disabled pending OQ-35–OQ-42 and the no-admin-clinical-payload policy.
- Following the user-approved OQ-02 decision, administrators can provision individual `HOME_CARE` and `LABORATORY` accounts and assign matching open requests to verified staff. Staff can list only requests assigned to their account, see only the service description and (for home care) location needed for the task, and update status within role-specific transitions. Home-care staff may mark an in-progress request resolved. Assigned laboratory staff can submit text-only results; an approved provider with the patient's active consent and care relationship must release a result before it is returned to the patient and appended to the record. This review gate is a conservative safeguard pending sponsor/legal confirmation. This is manual assignment, not external routing or facility organization management.
- Virtual consultations are not enabled because there is no approved consultation mode, external real-time service, recording/retention policy, or connection-failure workflow (OQ-20–OQ-23).
- The admin overview includes active/open request counts and indicates partial or disabled modules; it does not expose clinical payloads.

---

## 4. Security and authorization model

Security is a continuous requirement because MedNet holds patient health information.

### Core rules

- All protected endpoints require authentication.
- Authorization must combine role-based checks and patient relationship checks.
- Do not trust the client to send patient IDs or provider IDs.
- Use server-side validation at every entry point.
- Log only audit metadata, not raw tokens, passwords, or clinical payloads.
- Store secrets in environment variables, never in source control.

### Auth model

- JWT or session-based authentication for web clients
- scoped role checks: patient, provider, administrator, laboratory, homecare worker
- relationship checks for every patient-specific record
- explicit policy checks before returning medical history, vitals, lab results, or notes

### Audit expectations

Every sensitive operation should produce an audit event:

- provider approval
- patient registration
- record creation or update
- appointment change
- medication schedule change
- lab result upload
- admin action

---

## 5. Persistence and data model

Use PostgreSQL as the core persistence engine.

### Suggested domain data groups

- `patient` — patient profile, contact details, consent state
- `provider` — provider profile, credentials, specialties, availability
- `appointment` — bookings, status, notes, times
- `message` — conversation records and timestamps
- `record` — medical record entries and chronology
- `medication` — medication schedule and adherence status
- `vital` — measurement values and trends
- `homecare` — request status and assignment
- `laboratory` — request and result records
- `notification` — delivery status and event reference
- `admin` — dashboards, action logs, approvals

### Schema rules

- Use Flyway migrations for schema change tracking.
- Keep patient health data in secure, auditable tables.
- Use soft delete or archival patterns only when the product requires record retention.
- Avoid ad hoc schema edits in production.

---

## 6. Business logic boundaries

### Keep these in the domain/application layers

- patient identity validation
- appointment rules and status transitions
- provider approval rules
- medical record authorization
- medication adherence tracking
- notification event generation
- lab request routing
- user role and access policy checks

### Keep these in the infrastructure layer

- database access
- email/SMS integrations
- external video provider integration
- storage adapters
- authentication filters
- audit persistence

---

## 7. Quality and production standards

- Unit tests for business rules
- Integration tests for API endpoints and database state
- Testcontainers for PostgreSQL-backed tests
- Health endpoints for readiness and liveness
- Structured logging with request IDs
- metrics and tracing for operational health
- explicit timeout, retry and failure policy for outbound systems

---

## 8. Recommended backend conventions for MedNet

- Use full names in domain models, not just abbreviations.
- Use short names in technical layers only when the code becomes noisy.
- Keep `controller` thin; all logic belongs in services or domain rules.
- Use DTOs to isolate external contract shape from internal models.
- Use `@Transactional` only for business operations that require atomic consistency.
- Validate input at boundary and re-validate the domain layer when required.
- Use an explicit audit trail for any clinical write.

---

## 9. Immediate implementation priorities

1. authentication and authorization foundation
2. patient and provider aggregates
3. appointment lifecycle
4. medical record and record visibility rules
5. messaging and notification events
6. medication and vital flows
7. homecare and laboratory workflows
8. admin approvals and reporting

These are the correct backend starting points based on the product requirements in [req.md](req.md).

## 10. Full-stack and operational guardrails

- Spring Boot owns authentication, authorization, patient/provider relationship checks, business validation, clinical workflows, transactions, and audit behavior. The frontend is a client, never an authority.
- Every patient-specific operation must check both the actor's role and the actor-patient relationship and its scope. Never authorize solely from a client-supplied patient ID or a frontend route guard.
- Keep the authoritative REST API in Spring Boot under `/api/v1`; publish the contract with OpenAPI. Next.js route handlers must not duplicate core healthcare business rules.
- Keep audit metadata separate from ordinary application logs. Include a request ID across API responses and structured operational logs, and exclude credentials, tokens, and clinical payloads.
- Deploy the web app, API, and PostgreSQL as clearly bounded components behind HTTPS routing. Start without microservices, Kubernetes, Kafka, or a service mesh unless measured requirements justify them.
- Plan unit, API/security, database integration, and end-to-end tests. Use Testcontainers for PostgreSQL-backed behavior and verify health/readiness in deployment checks.

These remain engineering recommendations. Role definitions, identity and verification, patient-record visibility, client platform, hosting, and regulated workflows remain subject to the open questions in [req.md](req.md).
