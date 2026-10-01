# MedNet Backend and API Plan

This document is the dedicated backend and API design plan for MedNet. It complements the product-level requirements in [req.md](req.md) and focuses only on the server architecture, API contract, security model, domain boundaries, and production quality standards.

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
| Auth         | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/forgot-password` |
| Patient      | `/api/v1/patients/{id}`, `/api/v1/patients/{id}/records`                      |
| Provider     | `/api/v1/providers`, `/api/v1/providers/{id}/availability`                    |
| Appointment  | `/api/v1/appointments`, `/api/v1/appointments/{id}`                           |
| Message      | `/api/v1/conversations/{id}/messages`                                         |
| Medication   | `/api/v1/patients/{id}/medications`                                           |
| Vitals       | `/api/v1/patients/{id}/vitals`                                                |
| Home Care    | `/api/v1/home-care-requests`                                                  |
| Lab          | `/api/v1/lab-requests`, `/api/v1/lab-results/{id}`                            |
| Notification | `/api/v1/notifications`, `/api/v1/notifications/{id}/read`                    |
| Admin        | `/api/v1/admin/dashboard`, `/api/v1/admin/providers/pending`                  |

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
