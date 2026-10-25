# MedNet

MedNet is a two-service application: a Spring Boot API (`/`) and a Next.js web client (`web/`). The web service serves the public site and `/admin`; the API owns account authentication, session cookies, email verification, password recovery, MedNet roles, health checks, and PostgreSQL-backed workflows. Authentication messages are sent directly through EKDSend.

## Prerequisites

- Java 21
- Maven 3.9 or newer
- Node.js 20.9 or newer and npm
- PostgreSQL for persistent admin workflows

The API requires the PostgreSQL datasource for MedNet accounts, provider, clinical, and admin workflows. Configure the local account flow and EKDSend using [Authentication Setup](docs/AUTHENTICATION_SETUP.md).

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

Google sign-in is optional and appears when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are configured for the API. Register the callback URL listed in [Authentication Setup](docs/AUTHENTICATION_SETUP.md).

## Verify

Run the backend integration tests from the repository root. They use an in-memory H2 database in PostgreSQL mode and do not require a local PostgreSQL server:

```sh
mvn test
```

To verify the migrations and the full workflow suite against real PostgreSQL, provision the dedicated
test role and database described in [PostgreSQL setup](docs/POSTGRESQL_SETUP.md), keep their settings in
the git-ignored `.env.test.local`, and run:

```sh
set -a; . ./.env.test.local; set +a
mvn -Ppostgres-it verify
```

Each run empties the test database before migrating it. The run is refused unless the database name
ends in `_test`, the test role is not a superuser and it owns the database. Never point it at production
or a database containing data you need to preserve.

Run the web checks from `web/`:

```sh
npm run lint
npm run build
```

## Current scope

The backend supports account authentication, self-scoped patient profiles, provider applications and an approved-provider directory, availability, patient appointment requests with provider confirmation and in-app reminders 24 hours and 1 hour before confirmed appointments, consent-gated append-only records, text-only messaging for an established care relationship, provider-recorded text consultation outcomes, patient-entered medication schedules with daily in-app reminders, self-reported vitals, in-app notifications, patient-owned home-care/laboratory request tracking, admin-assigned partner request queues, and audit metadata. Patient service-request, medication, partner queue, conversation, and record-consent histories support optional pagination (default size 25, maximum 100) while preserving existing list responses. Patient request histories and partner queues also accept optional `status` and `search` filters; search is trimmed, case-insensitive, limited to 120 characters, and searches only request-service descriptions and locations visible to that role. Invalid statuses and oversized searches return `400`. Partner queue results and filters are restricted to the authenticated staff account's assigned requests and requested type. Laboratory and home-care staff use individually authenticated, least-privilege accounts and can access only requests assigned to their account and matching their role. Patients' medication, vital, and consultation outcome entries are included in their record timeline; provider access still requires an active approved provider, an eligible care relationship, and explicit patient consent.

This is a guarded MVP, not a complete clinical or service-delivery launch. Confirmed appointments can open participant-only text consultations with persisted, paginated messages; there is no video/voice integration or emergency workflow. Assigned home-care staff can schedule a future visit and update its progress. Assigned laboratory staff can enter text-only results; an approved provider with a confirmed care relationship and active patient consent must review and release them before patient access. The release is appended to the patient record. External routing, binary files, message attachments, audiovisual consultations, SMS/email/push, prescribing, medication interaction checks, and vital thresholds/alerts remain disabled pending the requirements and operational controls documented in [the requirements document](docs/req.md). Admin service-request APIs expose only opaque IDs and statuses, not patient request details or clinical payloads; assigned staff see only the minimum request details needed for their assignment. The API uses in-app notifications only. PostgreSQL-backed audit persistence is required for workflows; H2 is used by the current integration suite.

Provider availability slots may now be explicitly marked `IN_PERSON` or `TEXT`. Existing clients that omit `consultationMode` continue to create `TEXT` slots for compatibility. Only confirmed `TEXT` appointments can open a text consultation; audiovisual modes are not offered.

Product, safety, and platform decisions remain open in [the requirements document](docs/req.md). The architecture and API guidance are in [Production Architecture Guide](docs/PRODUCTION_ARCHITECTURE_GUIDE.md) and [Backend/API Plan](docs/backend-api-plan.md).
