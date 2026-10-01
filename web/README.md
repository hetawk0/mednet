# MedNet Web

The public-facing Next.js app is a separate client for the Spring Boot API. Its pages describe proposed features and development status; account and healthcare workflows are not live. The web platform remains provisional pending OQ-50 in `../docs/req.md`.

## Run locally

```sh
npm install
npm run dev
```

Open `http://localhost:3000`. Run the API separately from the project root with `mvn spring-boot:run`.

## Verify

```sh
npm run lint
npm run build
```

## Current routes

- `/` — overview and development status
- `/services` — proposed feature list
- `/about` — implementation approach and unresolved launch decisions
