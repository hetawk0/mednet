# MedNet API

The initial backend foundation is a Java 21 and Spring Boot 4.1 modular monolith. At this stage it exposes only Spring Boot Actuator health probes; patient, provider, authentication, and clinical APIs are intentionally not implemented until the open product and safety decisions in `docs/req.md` are confirmed.

## Requirements

- Java 21
- Maven 3.9 or newer

## Run

```sh
mvn spring-boot:run
```

The health endpoint is available at `http://localhost:8080/actuator/health`. Liveness and readiness probes are available at `/actuator/health/liveness` and `/actuator/health/readiness`. Health details are not exposed.

## Verify

```sh
mvn test
```

The first test starts the application context and verifies that its health status is `UP` without requiring a database.