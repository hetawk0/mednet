# MedNet Web

The Next.js app is the web client for the Spring Boot API. It contains the public product overview plus the initial authenticated account and administrator workflows. Clinical workflows and patient health records are not implemented; see `../docs/req.md` for the remaining product decisions.

## Run locally

```sh
npm install
npm run dev
```

Open `http://localhost:3000`. Run the API separately from the project root with `mvn spring-boot:run`.

The local API runs at `http://localhost:8080`; `next.config.ts` proxies `/api/*` and `/actuator/*` to it during development. Configure the initial administrator and datasource in the root `.env` before using `/admin`. See `../docs/ADMIN_SETUP.md` for the required variables.

## Verify

```sh
npm run lint
npm run build
```

## Current routes

- `/` — overview and development status
- `/services` — proposed feature list
- `/about` — implementation approach and unresolved launch decisions
- `/sign-in` — Google sign-in entry point when OAuth is enabled
- `/account` — authenticated account session view
- `/admin` — administrator sign-in and initial workflow console
