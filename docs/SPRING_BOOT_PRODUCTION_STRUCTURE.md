# MedNet Spring Boot Production Structure

This document defines a standard, production-oriented project layout for MedNet and future Spring Boot projects. It is designed for a modular monolith: one deployable application with explicit domain boundaries, scalable package organization, clean security boundaries, and short, readable naming conventions.

The goal is to keep the codebase:

- easy to navigate
- easy to test
- easy to scale
- safe for production work
- consistent across teams
- understandable to future engineers

---

## 1. Core principles

1. Keep one deployable app first; split by domain, not by microservice.
2. Use module boundaries around business capability, not just technical concerns.
3. Make package names short and standard.
4. Separate API, application logic, domain logic, persistence, security, and infrastructure.
5. Keep domain access rules explicit and auditable.
6. Treat patient and provider data as protected business data with strict authorization rules.
7. Use clear naming conventions and never allow vague names like `misc`, `thing`, `info`, `datax`, or `utilx`.

---

## 2. Naming conventions

### 2.1 Short names and full names

| Short name | Full meaning                   | Typical usage                                |
| ---------- | ------------------------------ | -------------------------------------------- |
| `auth`     | authentication / authorization | login, roles, security checks                |
| `appt`     | appointment                    | scheduling and booking                       |
| `msg`      | message                        | conversations/messages                       |
| `med`      | medication                     | medication schedules and adherence           |
| `notif`    | notification                   | reminders and alerts                         |
| `admin`    | administration                 | admin dashboard and approvals                |
| `dto`      | data transfer object           | request and response payloads                |
| `svc`      | service                        | business-layer services                      |
| `repo`     | repository                     | database access layer                        |
| `cfg`      | configuration                  | config classes                               |
| `util`     | utility                        | shared helper methods                        |
| `aud`      | audit                          | audit logs, traceability, and change history |
| `evt`      | event                          | domain event publishers and handlers         |
| `val`      | validation                     | validation logic                             |
| `sec`      | security                       | security filters and policy                  |
| `api`      | API                            | HTTP layer                                   |
| `dom`      | domain                         | shared domain concepts                       |

### 2.2 Standard naming rules

- Use lowercase package names.
- Use UpperCamelCase for Java classes.
- Use lowerCamelCase for methods and variables.
- Use kebab-case only for folders outside Java package paths when needed.
- Keep names short and recognizable.
- Prefer `patient` over `person` or `client` when the product is medically scoped.
- Prefer `appointment` over `appt` in public-facing APIs and business-level domain names.
- Prefer `medication` over `med` in patient-facing and clinical descriptions.
- Prefer `notification` over `notif` in user-facing text and documentation.
- Never create a new short name without documenting it in the project naming table.

### 2.3 Terms that should stay full

| Term       | Reason                                                        |
| ---------- | ------------------------------------------------------------- |
| `patient`  | Already clear and natural in code and APIs                    |
| `provider` | Clear and not noisy enough to justify abbreviation            |
| `record`   | Common domain concept; full name reads better                 |
| `vital`    | Full term is readable and specific                            |
| `home`     | Usually clear enough in context                               |
| `lab`      | Acceptable, but prefer the full word in human-facing sections |
| `document` | Full name is clear and easier to read                         |
| `schedule` | Full name is preferred unless repeated heavily                |

---

## 3. Standard project layout

```text
mednet/
├── .env.example
├── .gitignore
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── docker-compose.yml
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── security/
│   ├── img-structure/
│   │   └── README.md
│   └── SPRING_BOOT_PRODUCTION_STRUCTURE.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── mednet/
│   │   │           ├── MedNetApplication.java
│   │   │           ├── api/
│   │   │           │   ├── controller/
│   │   │           │   │   ├── auth/
│   │   │           │   │   ├── patient/
│   │   │           │   │   ├── provider/
│   │   │           │   │   ├── appointment/
│   │   │           │   │   ├── message/
│   │   │           │   │   ├── record/
│   │   │           │   │   ├── medication/
│   │   │           │   │   ├── vital/
│   │   │           │   │   ├── homecare/
│   │   │           │   │   ├── laboratory/
│   │   │           │   │   ├── notification/
│   │   │           │   │   └── admin/
│   │   │           │   ├── dto/
│   │   │           │   │   ├── auth/
│   │   │           │   │   ├── patient/
│   │   │           │   │   ├── provider/
│   │   │           │   │   ├── appointment/
│   │   │           │   │   ├── message/
│   │   │           │   │   ├── record/
│   │   │           │   │   ├── medication/
│   │   │           │   │   ├── vital/
│   │   │           │   │   ├── homecare/
│   │   │           │   │   ├── laboratory/
│   │   │           │   │   ├── notification/
│   │   │           │   │   └── admin/
│   │   │           │   ├── mapper/
│   │   │           │   ├── validation/
│   │   │           │   └── docs/
│   │   │           │
│   │   │           ├── app/
│   │   │           │   ├── service/
│   │   │           │   │   ├── auth/
│   │   │           │   │   ├── patient/
│   │   │           │   │   ├── provider/
│   │   │           │   │   ├── appointment/
│   │   │           │   │   ├── message/
│   │   │           │   │   ├── record/
│   │   │           │   │   ├── medication/
│   │   │           │   │   ├── vital/
│   │   │           │   │   ├── homecare/
│   │   │           │   │   ├── laboratory/
│   │   │           │   │   ├── notification/
│   │   │           │   │   └── admin/
│   │   │           │   ├── facade/
│   │   │           │   ├── event/
│   │   │           │   └── policy/
│   │   │           │
│   │   │           ├── domain/
│   │   │           │   ├── auth/
│   │   │           │   ├── patient/
│   │   │           │   ├── provider/
│   │   │           │   ├── appointment/
│   │   │           │   ├── message/
│   │   │           │   ├── record/
│   │   │           │   ├── medication/
│   │   │           │   ├── vital/
│   │   │           │   ├── homecare/
│   │   │           │   ├── laboratory/
│   │   │           │   ├── notification/
│   │   │           │   ├── admin/
│   │   │           │   ├── common/
│   │   │           │   └── enums/
│   │   │           │
│   │   │           ├── infra/
│   │   │           │   ├── config/
│   │   │           │   ├── security/
│   │   │           │   │   ├── jwt/
│   │   │           │   │   ├── filter/
│   │   │           │   │   ├── provider/
│   │   │           │   │   └── policy/
│   │   │           │   ├── repository/
│   │   │           │   ├── entity/
│   │   │           │   ├── persistence/
│   │   │           │   ├── email/
│   │   │           │   ├── sms/
│   │   │           │   ├── storage/
│   │   │           │   ├── audit/
│   │   │           │   └── integrations/
│   │   │           │
│   │   │           ├── common/
│   │   │           │   ├── util/
│   │   │           │   ├── constant/
│   │   │           │   ├── exception/
│   │   │           │   ├── enum/
│   │   │           │   └── response/
│   │   │           │
│   │   │           └── shared/
│   │   │               ├── clock/
│   │   │               ├── id/
│   │   │               ├── audit/
│   │   │               └── event/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── logback-spring.xml
│   │       ├── messages.properties
│   │       └── validation.properties
│   └── test/
│       └── java/
│           └── com/
│               └── mednet/
│                   ├── api/
│                   ├── app/
│                   ├── domain/
│                   ├── infra/
│                   └── integration/
└── README.md
```

---

## 4. Layer responsibilities

### API layer

Purpose: HTTP transport, input validation, response generation, and request routing.

Contains:

- controllers
- request / response DTOs
- validation classes
- API documentation metadata
- authentication endpoints

Rules:

- controllers stay thin
- they should not contain business logic
- they delegate to application services

### Application layer

Purpose: orchestrate use cases and business workflows.

Contains:

- service classes
- facades
- event dispatchers
- use-case handlers
- orchestration logic

Rules:

- this is the main business orchestration layer
- it coordinates domain rules and infrastructure services

### Domain layer

Purpose: model rules and core business logic.

Contains:

- entities
- value objects
- domain services
- enums
- business validation rules

Rules:

- domain should be free of HTTP concerns
- it should not depend on controllers or web frameworks
- domain logic should be testable in isolation

### Infrastructure layer

Purpose: persistence, security, messaging, integrations, and external services.

Contains:

- repositories
- JPA entities
- security filters
- config classes
- mail/SMS/video integration wrappers
- file storage adapters
- event publishers and consumers

Rules:

- infrastructure may depend on domain interfaces
- infrastructure should not contain business rules

### Common layer

Purpose: shared utilities and cross-cutting concerns.

Contains:

- exceptions
- constants
- response helpers
- enums
- formatting helpers

Rules:

- avoid dumping unrelated code here
- keep code reusable and well scoped

---

## 5. MedNet domain package organization

For MedNet, the domain groups should align with the product requirements:

- `auth` — login, registration, session, verification, roles
- `patient` — patient data and account lifecycle
- `provider` — provider directory, approval, profile, availability
- `appointment` — appointments and scheduling
- `message` — messages and conversation state
- `record` — medical records and documentation
- `medication` — medication schedules and adherence
- `vital` — patient measurements and trends
- `homecare` — home healthcare requests and fulfillment
- `laboratory` — laboratory requests and result handling
- `notification` — notifications, reminders, preferences
- `admin` — dashboard, approvals, audit, reports

This structure matches MedNet needs while still preserving one deployable application.

---

## 6. Recommended class naming examples

| Area       | Example class name             | Meaning                        |
| ---------- | ------------------------------ | ------------------------------ |
| API        | `AuthController`               | HTTP login and auth actions    |
| DTO        | `PatientRegisterRequest`       | patient registration payload   |
| Service    | `PatientService`               | patient workflow orchestration |
| Domain     | `Patient`                      | patient entity                 |
| Repository | `PatientRepository`            | persistence access for patient |
| Security   | `JwtAuthenticationFilter`      | JWT request filtering          |
| Validation | `PatientRegistrationValidator` | business validation            |
| Exception  | `ResourceNotFoundException`    | standard domain exception      |
| Audit      | `AuditLogService`              | traceable action tracking      |
| Event      | `AppointmentConfirmedEvent`    | domain event                   |

---

## 7. Production guardrails

Use these guardrails for all real Spring Boot work:

- Keep controllers thin and response-specific.
- Put business rules in services and domain objects.
- Validate all input at the API boundary.
- Use `@Transactional` only around business operations that require atomicity.
- Keep repositories focused on persistence and query operations.
- Do not put database access logic directly in controllers.
- Do not log raw secrets, tokens, user passwords, or clinical payloads.
- Keep all configuration in environment variables or `application.yml` with non-secret defaults.
- Add health checks, metrics, and audit logs for critical flows.
- Separate patient, provider, and admin routes by authorization level.

---

## 8. Standard package naming formula

Use this pattern when creating new features:

```text
com.mednet.{domain}.{layer}
```

Examples:

```text
com.mednet.patient.api.controller
com.mednet.patient.app.service
com.mednet.patient.domain.entity
com.mednet.patient.infra.repository
com.mednet.notification.api.dto
com.mednet.auth.infra.security
```

This formula stays short, predictable, and easy to expand.

---

## 9. Recommended production conventions for MedNet

- Prefer modular monolith with domain packages over a flat project.
- Keep one `application.yml` and environment-specific overrides.
- Place security and audit concerns beyond the controller layer.
- Keep patient consent, access control, and record visibility explicit.
- Add one audit log record for every change to sensitive clinical state.
- Use request IDs and structured logging in production.
- Version the API under `/api/v1`.
- Standardize response payloads and error envelopes.
- Keep naming consistent across database tables, entities, and services.

---

## 10. Final recommendation

For MedNet and similar future projects, the best standard is:

- Spring Boot + Java 21
- modular monolith architecture
- layered package structure
- short, consistent naming conventions
- domain-based package organization
- strong security and audit boundaries
- API versioning and structured error handling

This gives a clean production baseline without the operational cost of a distributed microservice system at the early stage.

---

## 11. Naming decision summary

Short names are good only when they are explicit and repeatable. The project should prefer the following pattern across all future code:

- `auth` for protection and session logic
- `patient` for patient-related pages and services
- `provider` for provider-related flows
- `appointment` for bookings and scheduling
- `message` for conversations
- `record` for records
- `medication` for medication flows
- `vital` for patient readings
- `homecare` for home healthcare
- `laboratory` for laboratory workflows
- `notification` for alerts and reminders
- `admin` for operational and approval workflows

This is the naming discipline we should maintain in folder names, package names, DTO names, service names, and test classes across MedNet and future projects.
