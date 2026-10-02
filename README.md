# MedNet

MedNet is a two-service application: a Spring Boot API (`/`) and a Next.js web client (`web/`). The web service serves the public site and `/admin`; the API owns authentication, health checks, and the PostgreSQL-backed administrative endpoints.

## Prerequisites

- Java 21
- Maven 3.9 or newer
- Node.js 20.9 or newer and npm
- PostgreSQL for persistent admin workflows

The API can start without PostgreSQL for health checks, but admin data workflows remain unavailable until a PostgreSQL JDBC URL is configured.

## Run locally

Start PostgreSQL and create an empty database named `mednet`. In a terminal at the repository root, create a private local environment file, edit its values, and start the API:

```sh
cp .env.example .env
# Edit .env with your local PostgreSQL and admin settings.
mvn spring-boot:run
```

Spring Boot imports the root `.env` file for local runs. The root `.gitignore` excludes `.env`; never commit it. Flyway applies versioned SQL migrations from `src/main/resources/db/migration` at API startup. The API listens on `http://localhost:8080` by default. Check health at `http://localhost:8080/actuator/health`.

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

Run the web checks from `web/`:

```sh
npm run lint
npm run build
```

## Current scope

The admin API supports provider application review, a non-clinical account registry, admin-entered service-request records, and audit events when PostgreSQL is configured. Account status changes are not yet enforced by patient/provider sign-in, and service requests are not yet connected to patient submission or notification flows. No clinical record payloads are exposed in the admin area.

Product, safety, and platform decisions remain open in [the requirements document](docs/req.md). The architecture and API guidance are in [Production Architecture Guide](docs/PRODUCTION_ARCHITECTURE_GUIDE.md) and [Backend/API Plan](docs/backend-api-plan.md).
