# MedNet engineering instructions

## Scope and architecture

- Prioritize the Spring Boot API and backend workflows. Change the web client only when needed to keep an API contract usable or safe.
- MedNet is a single-deployable modular monolith organized by feature. Follow the existing `com.mednet.<feature>.api`, `.app`, and `.data` slices; keep cross-cutting HTTP concerns in `com.mednet.api` and security/configuration in their existing packages.
- Keep controllers focused on HTTP, validation, authorization annotations, and response mapping. Put use-case orchestration and transactions in application services. Keep repositories and JPA entities in the feature's `data` package.
- Prefer the existing full domain names (`appointment`, `medication`, `notification`, `laboratory`) over abbreviations. Do not rename established packages or move the codebase into a different sample layout just to match a diagram.
- Keep the local `docs/img-structure/README.md` architecture reference aligned with the feature-first packages used in source.

## Backend correctness

- Keep the authoritative API under `/api/v1`; validate inputs at the API boundary and enforce authorization again in Spring Boot. Never rely on frontend route guards for access control.
- Preserve patient ownership, provider-care relationship, consent, and least-privilege staff checks. Do not expose clinical payloads to administrators or unrelated users.
- Add or update tests for changed behavior, including authorization and invalid-state paths. Use Flyway migrations for schema changes; add a new migration rather than editing an existing migration that may already have run.
- Keep API documentation, the frontend handoff, and backend scope documentation aligned with contract changes.
- No payment collection or payment-gated flows are in scope. Do not invent clinical, emergency, credential-verification, privacy, or regulatory policy; keep those workflows safely gated where requirements are unresolved.

## Verification and operational boundaries

- Use Java 21 and Maven for backend verification (`mvn test`). Use the PostgreSQL integration profile against a dedicated local PostgreSQL database when migrations or PostgreSQL behavior change.
- Do not use Docker. Do not add or modify CI/CD or deployment flow unless explicitly requested.
- Do not commit secrets, local environment files, generated build output, or unrelated working-tree changes.
