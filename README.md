# MedNet

MedNet is a two-service application: a Spring Boot API (`/`) and a Next.js web client (`web/`). The web service serves the public site and `/admin`; the API owns authentication, health checks, and the PostgreSQL-backed administrative endpoints.

## Prerequisites

- Java 21
- Maven 3.9 or newer
- Node.js 20.9 or newer and npm
- PostgreSQL for persistent admin workflows

The API can start without PostgreSQL for health checks and configured-super-admin access. Database-backed patient registration/sign-in, provider, clinical, and admin workflows remain unavailable until a PostgreSQL JDBC URL is configured.

## Run locally

Start PostgreSQL and create an empty database named `mednet`. In a terminal at the repository root, create a private local environment file, edit its values, and start the API:

```sh
cp .env.example .env
# Edit .env with your local PostgreSQL and admin settings.
mvn spring-boot:run
```

Spring Boot imports the root `.env` file for local runs. The root `.gitignore` excludes `.env`; never commit it. Flyway applies versioned SQL migrations from `src/main/resources/db/migration` at API startup. The API bounds its Hikari pool to five connections and retries Flyway connection attempts to handle brief shared-database saturation during deployments. The API listens on `http://localhost:8080` by default. Check health at `http://localhost:8080/actuator/health`.

The OpenAPI specification is available at `http://localhost:8080/api-docs` and Swagger UI at
`http://localhost:8080/swagger-ui` after administrator sign-in. In production, use
`https://mednet.lpad.ekddigital.com/api-docs` or
`https://mednet.lpad.ekddigital.com/swagger-ui`; these documentation routes are forwarded to
the API service and require an `ADMIN` or `SUPER_ADMIN` session. See the
[Frontend API Handoff](docs/FRONTEND_API_HANDOFF.md) for setup and integration guidance.

In a second terminal at the repository root, install and start the web client:

```sh
cd web
npm ci
npm run dev
```

Open `http://localhost:3000` for the public site or `http://localhost:3000/admin` for the administrator area. In development, the Next.js server proxies `/api/v1/*` to `http://localhost:8080`; set `MEDNET_API_URL` only if the API uses another local origin. In production, LPAD/nginx routes `/api/*` to the API service and `/` to the web service.

Do not reuse the example local credentials in production. Configure production secrets in LPAD as described in [Admin Setup](docs/ADMIN_SETUP.md).

For Google sign-in, enable OAuth in `.env` and register the local and production callback URLs listed in [Admin Setup](docs/ADMIN_SETUP.md). Google credentials are read only by Spring Boot.

## Verify

Run the backend integration tests from the repository root. They use an in-memory H2 database in PostgreSQL mode and do not require a local PostgreSQL server:

```sh
mvn test
```

To verify the Flyway migrations and persistence mappings against real PostgreSQL, configure
environment variables for a dedicated, empty test database and run:

```sh
export MEDNET_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/mednet_test
export MEDNET_TEST_DATABASE_USERNAME=mednet_test
export MEDNET_TEST_DATABASE_PASSWORD=replace-with-a-local-test-password
mvn -Ppostgres-it verify
```

The PostgreSQL profile applies migrations to the configured database. Never point it at production
or a database containing data you need to preserve.

Run the web checks from `web/`:

```sh
npm run lint
npm run build
```

## Current scope

The backend supports account authentication, self-scoped patient profiles, provider applications and an approved-provider directory, availability, patient appointment requests with provider confirmation, consent-gated append-only records, text-only messaging for an established care relationship, patient-entered medication schedules with daily in-app reminders, self-reported vitals, in-app notifications, patient-owned home-care/laboratory request tracking, admin-assigned partner request queues, and audit metadata. Laboratory and home-care staff use individually authenticated, least-privilege accounts and can access only requests assigned to their account and matching their role. Patients' medication and vital entries are included in their record timeline; provider access still requires an active approved provider, an eligible care relationship, and explicit patient consent.

This is a guarded MVP, not a complete clinical or service-delivery launch. Confirmed appointments can open participant-only text consultations with persisted, paginated messages; there is no video/voice integration or emergency workflow. Assigned home-care staff can schedule a future visit and update its progress. Assigned laboratory staff can enter text-only results; an approved provider with a confirmed care relationship and active patient consent must review and release them before patient access. The release is appended to the patient record. External routing, binary files, message attachments, audiovisual consultations, SMS/email/push, prescribing, medication interaction checks, and vital thresholds/alerts remain disabled pending the requirements and operational controls documented in [the requirements document](docs/req.md). Admin service-request APIs expose only opaque IDs and statuses, not patient request details or clinical payloads; assigned staff see only the minimum request details needed for their assignment. The API uses in-app notifications only. PostgreSQL-backed audit persistence is required for workflows; H2 is used by the current integration suite.

Provider availability slots may now be explicitly marked `IN_PERSON` or `TEXT`. Existing clients that omit `consultationMode` continue to create `TEXT` slots for compatibility. Only confirmed `TEXT` appointments can open a text consultation; audiovisual modes are not offered.

Product, safety, and platform decisions remain open in [the requirements document](docs/req.md). The architecture and API guidance are in [Production Architecture Guide](docs/PRODUCTION_ARCHITECTURE_GUIDE.md) and [Backend/API Plan](docs/backend-api-plan.md).
