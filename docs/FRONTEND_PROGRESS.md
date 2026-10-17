# MedNet Frontend Progress

## Current status

**Phase 5E (final UI/UX, accessibility and cross-role quality audit) is complete as of 2026-10-12 and awaiting
review.** Phase 5D is approved. The consolidated report is `docs/FINAL_FRONTEND_QA.md`. Still **blocked**,
unchanged: credential rotation and PostgreSQL re-verification; multi-instance limits; reverse-proxy
configuration; production CSP; review of the existing `postgres` database (`docs/SECURITY_AUDIT.md`).
Authentication was subsequently changed to MedNet-managed sessions and EKDSend email; older Google OAuth
and Supabase Auth references in this historical progress log are superseded by
[`AUTHENTICATION_SETUP.md`](AUTHENTICATION_SETUP.md).

## Fidelity correction log — header, footer and homepage (2026-10-17)

Requested: correct the public header, footer and homepage against the Figma reference
(https://dab-text-35201810.figma.site). Uncommitted, awaiting review.

- **Method.** Geometry and computed styles of reference and implementation captured with Playwright
  (Edge) at 1440, 1024, 768 and 390px, compared element by element, corrected, and re-measured.
  The Figma MCP was not authenticated, so the published Make prototype was the reference; no Figma
  frames or component properties were inspected directly.
- **Changes.** See `DESIGN_SYSTEM.md` §14.4. Files: `globals.css` (container width, three colour
  tokens), `public-header.tsx`, `public-footer.tsx`, `public-nav.ts`, `wordmark.tsx`, `ui/card.tsx`
  (`Eyebrow size`), `features/home/*`, new `features/public/pending-page.tsx` and six placeholder routes.
- **Decision (user, 2026-10-17).** The previously hidden links are rendered and lead to placeholder
  pages instead of staying hidden.
- **Verification.** Lint, typecheck, Vitest, and the mocked Playwright suite; results are in the
  completion report for this change.

## Phase 5E log — final quality audit

Full findings, classifications and blockers: `FINAL_FRONTEND_QA.md`. Fidelity measurements:
`DESIGN_SYSTEM.md` §22. Route audit: `PAGE_INVENTORY.md` §6.

### Found and fixed

| Severity | Finding | Fix |
|---|---|---|
| Medium | Dialogs could not be reopened right after Escape (the browser returns focus before the native `close` event; the next Enter was lost) | `ui/dialog.tsx` notifies the parent synchronously on user dismissal |
| Medium | 320px reflow: doctor record form (15px), activity and schedule rows (2–3px) overflowed; the top bar hid its eyebrow | Shrinkable grid track; rows wrap their badge; top bar wraps below 768px |
| Medium | Choosing a record did not move focus to its details in production builds | `record-layout.tsx` (first part of this phase) |
| Low | Doctor workspace: a failed application load offered no retry | "Try again" (`provider/application.tsx`) |
| Low | Unknown addresses showed Next's unbranded 404 | `app/not-found.tsx` |
| Low | Typography off the reference: row, audit, tile and preview titles (600 → 700), payout values, lifecycle, principle and agent card sizes, header links, wordmark | Matched to measured reference values |
| Low | Desktop table cells top-aligned (reference centres them) | `align-middle` |

No Critical or High defects were found.

### New tests

`e2e/audit.spec.ts` (74 route states × 3 widths: landmarks, overflow, clipping, link targets, 24px targets,
keyboard walk, axe; empty and unreachable-API states per workspace; 320px reflow), `e2e/audit-self-test.spec.ts`
(proves each sweep check fails on a planted defect), `e2e/support/quality.ts`, `e2e/keyboard.spec.ts`
(keyboard-only workflows), plus regression assertions in `fidelity.spec.ts`, `provider.spec.ts`,
`public.spec.ts` and `home-care.spec.ts`.

### Verification

Final run 2026-10-12, one heavy task at a time (free memory checked first, 3.3 GB).

| Check | Mode | Result |
|---|---|---|
| `mvn test` | H2 | **76/76** (no backend change this phase) |
| Lint, typecheck | — | pass |
| Vitest | mocks | **158/158** |
| Production build | — | pass, 69 routes |
| Full mocked Playwright suite (390/820/1440, incl. the sweep, self-test and keyboard specs) | `next dev`, 2 workers | **953 passed**, 43 skipped by design, 0 failed |
| Mocked suite against the production build (all specs except the three that use the development-only `/foundation` pages) | `next start`, previews enabled | **848 passed**, 22 skipped by design, 0 failed |
| Route gating on `next start`, default configuration | — | `/preview`, `/preview/finance`, `/foundation` **404**; security headers present, `X-Powered-By` absent |
| Real-API suites (8, incl. `pagination.live`) | H2 API, `next dev`, 1 worker | **206 passed**, 0 failed (`FINAL_FRONTEND_QA.md` §6) |
| PostgreSQL | PostgreSQL | **blocked — credential rotation** |

The 43 skips are viewport-conditional: `fidelity.spec` runs at 1440px only (20), the 320px reflow pass runs
once from the 390px project (18), the sweep self-test runs once (4), and the rail-label check does not apply at
1440px (1). The production run skips only the reflow and self-test duplicates (22).

## Phase 5D log — performance, pagination and cleanup

### Method

Measured, not assumed. A throwaway H2 API was seeded through the real endpoints with synthetic data past
the old limits: 111 approved providers with open times, one doctor with 30 care patients, 120 laboratory
requests with 120 results awaiting review, and the resulting queue and audit rows. A Playwright script
loaded each affected page three times as the relevant role (`next dev`, one worker) and recorded API
requests, response bytes, time until the content was visible (median) and what the page reported.
Baseline before any data-loading change; the same data and script after.

### Confirmed bottlenecks (baseline)

| Page | Finding |
|---|---|
| Homepage | Loaded 100 providers to count specialties, so counts were wrong past 100 (13 shown, 14 real); then one availability call per featured doctor |
| Find care | One availability call per listed provider: 13 requests per results page |
| Provider opened by link (`?provider=`) | Loaded the first 100 providers to find one; **past 100 the drawer never opened** |
| Doctor clinical work and overview | First 100 appointments, then one `…/lab-results/pending` call per patient, **stopping at 25 patients**: 28 requests, 100 of 120 results counted |
| Admin audit | Latest 100 events only; search and record filter ran in the browser over those 100 |
| Admin provider applications, operational references, home-care and laboratory queues | Latest 100 only, status and text filters in the browser |
| Laboratory results | Latest 100 submissions only, also used for every order badge |
| Patient released results | Latest 100 only |
| Not a defect | The second `/admin/service-requests` call on the task queue is the other tab (home care, then laboratory) |

### Results — before and after (same data, median of 3 loads)

| Page | API requests | Response bytes | Time to content | Correctness |
|---|---|---|---|---|
| Homepage | 4 → **2** | 16.4 KB → **1.3 KB** | 385 → 319 ms | specialty counts now cover every provider |
| Find care | 13 → **1** | 7.5 KB → **2.6 KB** | 258 → 248 ms | — |
| Provider beyond the first 100 | 16 → **4** | 23.3 KB → **3.4 KB** | 802 → 787 ms | drawer **opens** (was broken) |
| Doctor clinical work | 28 → **3** | 38.3 KB → **4.0 KB** | 221 → 205 ms | **120 of 120** results (was 100) |
| Admin audit | 2 → 3 | 26.1 KB → **6.7 KB** | 697 → 717 ms | whole trail, filters on the server |
| Admin laboratory queue | 4 → 4 | 18.1 KB → **5.5 KB** | 780 → 729 ms | all 120 requests reachable |
| Laboratory results | 3 → 5 | 53.7 KB → 52.8 KB | 281 → 217 ms | **120** awaiting review counted (was 100) |

Request counts, bytes and correctness are measured improvements. Load times on a local H2 database barely
change (within run-to-run noise); the gains in time are expected, not measured, for a remote database
where each avoided request saves a round trip (about 200 ms each against Supabase in 5A). The laboratory
results page stays near 53 KB because the assigned-order list (`GET /partner/service-requests`, 120 orders
in the test) is still unpaged; see remaining work.

### Changes

| Area | Change |
|---|---|
| Directory API | `ProviderDirectoryEntry.nextAvailableAt` (one grouped query per page); `GET /providers/{id}` and `GET /providers/specialties` (public, approved providers only) |
| Doctor review queue | `GET /providers/me/lab-review-queue` (PROVIDER): care patients with pending results (record consent granted) or without record access, paged, with totals; counts and dates only |
| Paged admin lists | `?page=` on `/admin/providers` (+ `search`), `/admin/requests`, `/admin/audit` (+ `search`, `resourceType`, `labelAction`), `/admin/service-requests` (+ repeatable `status`); `GET /admin/audit/resource-types` |
| Paged result lists | `?page=` on `/partner/lab-results` (+ `status`) and `/patients/me/lab-results` |
| Laboratory order list | `PartnerRequestDetails.latestResult` (review state, no text) for laboratory orders |
| Web | find care and homepage use the new directory fields; clinical work and the doctor overview use the review queue; admin audit, providers, task queues, laboratory results and patient laboratory results page through the server with `Pagination`; shared `useDebouncedValue`/`usePageFor` (`features/workspace/paging.ts`); `buildApiUrl` repeats array query values |
| CSS | Unused pre-reference classes and variables removed from `globals.css` (see `DESIGN_SYSTEM.md` §10) |

Every list endpoint keeps its original response when `page` is absent, so existing callers are unaffected.

### Verification (completed 2026-10-11 after the memory-limited first attempt)

Run one heavy task at a time (one Playwright worker; one API and one `next dev` for the real-API suites;
free memory checked between suites, lowest 0.9 GB during the mocked suite, no critical stop needed).

| Check | Mode | Result |
|---|---|---|
| `mvn test` | H2 | **76/76** (+4 Phase 5D tests, each with negative authorization cases) |
| Lint, typecheck | — | pass |
| Vitest | mocks | **158/158** |
| Production build | — | pass, 69 routes incl. `/home-care/*`; on `next start` `/preview`, `/preview/finance` and `/foundation` answer **404** by default |
| Full mocked Playwright suite (390/820/1440, axe WCAG 2.2 AA in every page spec) | mocked API | **660 passed**, 15 skipped by design (viewport-specific checks), 0 failed |
| Computed-style comparison, 18 pages × 3 widths | mocked API | **0 of 54 differ** (`DESIGN_SYSTEM.md` §21) |
| Real-API suites, fresh H2 database | H2 API, `next dev`, 1 worker | admin **27/27**, find-care **24/24** (after a test fix, below), patient **33/33**, provider **24/24**, laboratory **36/36**; auth **30/30** and home-care **24/24** from the first attempt (no change since affects them) |
| `pagination.live.spec.ts` (new) | H2 API | **8/8** at 1440px; 16 skipped by design at 390/820 (seeds 100+ rows; the Pagination control is covered at all widths by the mocked suites) |
| PostgreSQL | PostgreSQL | **blocked — credential rotation** |

**Targeted pagination checks** (`pagination.live.spec.ts`, 105+ rows seeded through the API): a provider
past the first 100 opens from a link with its next open time; directory search counts all 105 and page 2
keeps the search in the URL; admin application search and status filter return all 105 and keep both
filters on page 2, and find the single 000 entry; the audit trail (over 100 events) is searchable by its
readable label across pages; the admin laboratory queue reaches page 2 with the "open" filter kept and no
resolved or cancelled rows; laboratory results count all 106 awaiting review and page within the
laboratory's own work (another laboratory sees 0); the doctor's queue counts 106 results, lists the
patient without access, and after one patient revokes consent counts 1, moves that patient to "Records not
shared", returns no result text and refuses that patient's pending results (`403`); paged endpoints refuse
the wrong roles (`403`) and signed-out callers (`401`).

**Accessibility:** no axe violations on any mocked page, including the new pagination controls and the
reworked admin, laboratory, doctor and patient lists; keyboard and focus specs pass.

**Failures found and fixed (none were application regressions):**

| Failure | Cause | Correction |
|---|---|---|
| First attempt: run stopped; admin `0xC0000142` worker errors | Machine low on memory (other applications) | Reran one task at a time |
| `find-care.live` "hero search": expected "Tomorrow", saw "Tue 13 Oct" (3 viewports) | Test seeded "tomorrow" as now + 26 h; after 22:00 GMT that is two days ahead | Slots seeded at 12:00 and 13:00 GMT tomorrow (`tomorrowAt`); suite rerun 24/24 |
| `pagination.live` queue test: strict-mode locator error | New test matched the status field in both tab panels | Scoped to the laboratory tab panel; spec rerun 8/8 |

### Remaining pagination and performance gaps (not paged — not claimed as done)

| List | Today | Smallest backward-compatible change | Priority |
|---|---|---|---|
| Appointments (patient and doctor appointment views, doctor patients, schedule, messages, overviews) | Newest 100; the appointment views say "Showing the … most recent of N" | Optional `status` (repeatable) and `from`/`to` (or `scope=upcoming\|past`) on `GET /appointments`, same `page`/`size`; views page per tab | 1 — grows fastest for active doctors |
| Partner queues `GET /partner/service-requests` (laboratory, home care) | Complete but unbounded; dominates the laboratory results page (53 KB with 120 orders) | `?page=` returning a page (as 5D), with `status` filter; badge data already in each row | 2 |
| Patient home-care and laboratory request lists | Complete, unbounded | `?page=` on `/home-care-requests` and `/lab-requests` | 3 |
| Conversations `GET /conversations` | Complete, unbounded | `?page=` | 3 |
| Admin staff picker (`/admin/accounts?size=100` per type) | First 100 verified staff | Search box using the existing paged, searchable accounts endpoint | 3 |
| `GET /patients/{id}/lab-results/pending` (one patient) | At most 100 | `?page=` | 4 — 100 pending results for one patient is unlikely |

Measured time savings were small on local H2; repeat the before/after measurement against PostgreSQL once
credentials are rotated.

## Phase 5C log — home-care workspace

### Audit (before changes)

| Item | Finding |
|---|---|
| Endpoints | `GET /partner/service-requests?type=HOME_CARE`, `PATCH …/{id}/schedule`, `PATCH …/{id}/status` (`PartnerServiceRequestController`, class-level `LABORATORY` or `HOME_CARE`; schedule `HOME_CARE` only) |
| Who | Active, email-verified `HOME_CARE` account; only requests assigned to it (others `404`); type `LABORATORY` or `/partner/lab-results` `403` |
| Data shared with staff | `PartnerRequestDetails`: care requested and visit location (patient's words), status, visit time, created/updated. No patient name, contact, account ID or records |
| Transitions | Staff: set visit time once (OPEN, unscheduled, future) → IN_PROGRESS (OPEN with a time) → RESOLVED (IN_PROGRESS). Patient: cancel while OPEN. Administrator: assign (OPEN, unassigned, verified active `HOME_CARE`), cancel while OPEN or IN_PROGRESS. No reschedule, unassign, notes or patient contact |
| Assignment | Admin task queue → "Home-care requests" (Phase 4E/4F UI, no change needed) |
| Patient side | `/patient/appointments?tab=home-care`: request (care needed, visit location), cancel while open, sees status and visit time |
| Redirects before 5C | `HOME_CARE` → `/account` (web) and Google → `/account` (API) |
| Notifications | Staff are notified of assignment; patients of scheduling and each status change (existing) |

### Changes

| Area | Change |
|---|---|
| Web routes | `/home-care` (overview), `/home-care/visits`, `/home-care/help`, `/home-care/profile` (`app/home-care/*`) |
| Web feature | `features/home-care/`: `home-care-api.ts`, `status.ts` (stage, badge, `HCR-` reference, counts, timeline, Liberia-time input), `home-care-workspace.tsx` (HOME_CARE guard, shared queue, shell), `visits.tsx` (queue, detail, schedule/start/complete with confirmation, stale handling), `views.tsx` (overview, visits, profile, help) |
| Routing | `homePathForRole("HOME_CARE")` → `/home-care`; `/account` forwards home-care staff; registry entry `home-care` (numbered menu) |
| Backend | `SecurityConfig.googleSignInDestination("HOME_CARE")` → `/home-care` (was `/account`) — the only backend change; no authorization rule changed |
| Tests | `MedNetApplicationTests.homeCareRequestsStayWithTheAssignedStaffMember` (new; also runs in `PostgreSqlWorkflowIT`); `home-care.test.ts`; `home-care.spec.ts` + `support/home-care-stub.ts`; `home-care.live.spec.ts`; updated `laboratory.spec.ts`, `workspaces.test.ts`, `destination.test.ts`, `auth-panels.test.tsx`, `SecurityConfigTests` |
| Docs | this file, `FRONTEND_IMPLEMENTATION_PLAN.md`, `SECURITY_AUDIT.md`, `FRONTEND_API_HANDOFF.md`, `PAGE_INVENTORY.md`, `DESIGN_SYSTEM.md` §20, `web/e2e/README.md` |

### Supported and unavailable

| Supported (real API) | Unavailable (stated in the UI) |
|---|---|
| See own assigned requests; care requested and location | Patient name or contact details |
| Set the visit time once | Changing the visit time (contact an administrator) |
| Start the visit; mark it completed | Visit notes, care records, travel tracking, dispatch |
| See cancellations (patient or administrator) | Declining or handing back a request |
| Account summary; password reset via sign-in page | Editing name or email (administrator) |

### Results

| Check | Mode | Result |
|---|---|---|
| `mvn test` | H2 | **72/72** (+1 home-care authorization test) |
| Vitest | mocks | **157/157** (+6 home-care, +1 routing) |
| Lint, typecheck | — | pass |
| Mocked Playwright, full suite (390/820/1440) | mocked API | **660 passed**, 15 skipped by design (viewport-specific); one existing patient redirect test updated for `HOME_CARE` → `/home-care` |
| `home-care.spec.ts` (20 tests × 3 widths, WCAG 2.2 AA via axe, no horizontal scroll, 40px targets) | mocked API | **60/60** |
| Production build | — | pass; `/home-care/*` served as session-guarded shells (no request data in server HTML), security headers present |
| Real-API suites incl. `home-care.live` (24) | H2 API, `next dev` | **198/198** (home-care 24, auth 30, admin 27, find-care 24, patient 33, provider 24, laboratory 36); no rate limit tripped |
| PostgreSQL ITs and live suites | PostgreSQL | **blocked — credential rotation** |

## Phase 5B closure log — rate limiting and session security

### Changes

| Change | Files | Tests |
|---|---|---|
| In-process rate limits: sign-in failures (10/account, 50/client per 15 min), email-sending endpoints (5/address, 20/client per hour), reset-code checks (10/address per hour, 30/client per 15 min), verification links (30/client per 15 min); `429` + `Retry-After`, one generic message | `auth/security/AuthRateLimiter`, `AuthRateLimitProperties`, `SignInRateLimitFilter`, `RateLimitExceededException`, `SecurityConfig`, `auth/api/AccountAuthController`, `api/ApiExceptionHandler`, `application.yml` | `AuthenticationAbuseTests` (9 of 13), `AuthRateLimiterTests` (5) |
| Client address from the TCP peer; `X-Forwarded-For` only from `MEDNET_TRUSTED_PROXIES` | `auth/security/ClientAddressResolver` | 3 tests |
| Sessions end on password reset, admin password/email/role change, suspension, verification withdrawn, deletion (they used to keep old access) | `auth/security/AccountSessions`, `SecurityConfig` (session registry), `AccountAuthController`, `admin/app/AdminWorkflowService` | 2 tests, both fail with the fix switched off |
| Absolute session lifetime 12 h (`SESSION_ABSOLUTE_TIMEOUT`), idle 30 min made explicit (`SESSION_IDLE_TIMEOUT`) | `auth/security/SessionLifetimeFilter`, `application.yml` | 1 test |
| Sign-out regression | — | 1 test |
| Web: `429` fallback message | `web/src/lib/api/errors.ts` | `client.test.ts` |
| New settings documented as placeholders | `.env.example` | — |

### Results — database-independent only

| Check | Database / mode | Result |
|---|---|---|
| `mvn test` | H2 | **71/71** (53 before; +13 abuse/session, +5 limiter unit) |
| Real-API browser suites with limits and session controls on | H2 file database, `next dev` | **174/174** (auth 30, admin 27, find-care 24, patient 33, provider 24, laboratory 36) |
| Frontend lint, typecheck | — | pass |
| Vitest | mocks | **150/150** (+1 rate-limit message) |
| Production build | — | pass |
| OSV (Maven, 154 artifacts) | — | 0 advisories |
| `npm audit` | — | 0 production; 5 development (one `braces` advisory, no fix available) |
| PostgreSQL ITs and live suites | PostgreSQL | **blocked — credential rotation** |
| Google OAuth end to end | Google | **blocked — no OAuth client** |

No limit tripped during the real-API browser runs (no `rate limit reached` log line), so the defaults
do not affect normal use. The H2 API ran with a generated throwaway administrator password and a new
H2 file in the session scratchpad; environment variables overrode the Supabase settings in `.env`.

## Phase 5B log — security audit (offline)

### Credential rotation

The user reported the password as reset, but `.env` and `.env.test.local` still held the exposed
value (compared without displaying it) and one read-only sign-in with it succeeded. The user then
authorized offline work only and forbade further connections with that password. No connection
was made after that. Rotation, re-verification and the PostgreSQL re-runs remain open.

### Fixes (each with a test that failed first where it reproduces a defect)

| Fix | Files | Test |
|---|---|---|
| Sign-up no longer replaces the password of an existing unverified account (staff-account takeover before verification) | `AccountAuthController` | `signUpCannotReplaceThePasswordOfAnExistingUnverifiedAccount` |
| Google sign-in drops a password that was never confirmed by the mailbox owner (pre-registration takeover) | `GoogleOAuth2UserService` | `googleSignInDropsAPasswordThatWasNeverConfirmedByTheMailboxOwner`, `googleSignInKeepsThePasswordOfAVerifiedAccount` |
| API deny-by-default; only health, auth entry points, `GET /providers` and `GET /providers/*/availability` are public | `SecurityConfig` | `signedOutCallersGetUnauthorizedFromEveryAccountEndpoint` (31 endpoints; also asserts `no-store`, `nosniff`, `DENY` headers) |
| Spring Boot 4.1.0 → 4.1.1 with Tomcat 11.0.26 and Jackson 3.1.7 / 2.21.7 overrides (critical Tomcat and Spring MVC, high Jackson and PostgreSQL JDBC advisories) | `pom.xml` | OSV scan 0 advisories; H2 suite |
| Web security headers and no `X-Powered-By` | `web/next.config.ts` | checked on `next start` |
| `NEXTAUTH_URL` placeholder (public origin for email links) | `.env.example` | — |

### Results — offline only

| Check | Database / mode | Result |
|---|---|---|
| `mvn test` | H2 | **53/53** (was 49; +4 security tests) |
| Maven OSV advisory scan (154 artifacts) | — | 0 (was 8 artifacts with advisories) |
| `npm audit` | — | 0 production; 5 high development findings = one `braces` advisory with no patched release (deferred, see audit) |
| Frontend lint, typecheck | — | pass |
| Vitest | mocks | **149/149** |
| Production build | — | pass |
| Production `next start`: `/preview/**`, `/foundation/**` | no API | 404 by default; previews 200 only with `MEDNET_PREVIEW_WORKSPACES=enabled` |
| `preview.spec.ts` on the production build, previews enabled | no API | **54/54** (no `/api/` request, controls inert, WCAG AA) |
| Mocked Playwright suite (`next dev`, 2 workers) | mocked API | **600 passed**, 15 skipped by design (viewport-specific checks) |
| `PostgreSqlIntegrationIT`, `PostgreSqlWorkflowIT`, six live-API suites | PostgreSQL | **blocked — credential rotation** |
| Google OAuth end to end | Google | **not run — no OAuth client configured** |

### Files (5B)

- Backend: `pom.xml`, `src/main/java/com/mednet/auth/security/SecurityConfig.java`,
  `…/auth/security/GoogleOAuth2UserService.java`, `…/auth/api/AccountAuthController.java`.
- Tests: `src/test/java/com/mednet/MedNetApplicationTests.java`,
  `…/auth/security/GoogleOAuth2UserServiceTests.java`.
- Web: `web/next.config.ts`.
- Config and docs: `.env.example`, `docs/SECURITY_AUDIT.md` (new), `docs/POSTGRESQL_SETUP.md`,
  `docs/ADMIN_SETUP.md`, `docs/FRONTEND_API_HANDOFF.md`, `docs/FRONTEND_IMPLEMENTATION_PLAN.md`,
  this file.

## Phase 5A log — PostgreSQL integration

### Environment audit

| Item | Finding |
|---|---|
| Local server | PostgreSQL 18, Windows service `postgresql-x64-18`, running, port 5432, listening on `0.0.0.0` and `::` (remote hosts still refused: `pg_hba.conf` only admits 127.0.0.1 and ::1, all `scram-sha-256`; left unchanged) |
| API configuration | `application.yml` imports `.env` as a properties file; datasource only from `SPRING_DATASOURCE_*`; Hikari pool 5/1; Flyway 10 retries × 2 s |
| Migrations | Flyway V1–V15, PostgreSQL dialect; no H2-only SQL |
| H2 use | `MedNetApplicationTests` (`jdbc:h2:mem` in PostgreSQL mode, 26 workflow tests); every live-API browser run in 4A–4G |
| PostgreSQL tests before 5A | `postgres-it` Maven profile (Failsafe) with `PostgreSqlIntegrationIT` (2 tests) — never run on this machine (Failsafe had never been downloaded) |
| Tests that stay on H2 | `mvn test` (fast regression, 49 tests); `NoDatabaseStartupTests` needs no database |
| Docker | Not used (`AGENTS.md`); none in the repository |

### Root cause of the rejected credentials

The local server log: `password authentication failed for user "mednet"` — `DETAIL: Role "mednet" does
not exist.` The `.env` credentials from 2026-10-10 were generated but the role and database were never
created. No local superuser credential was available, and a provisioning script handed over for that
step did not reach the server. The user then supplied a **disposable Supabase project** instead, now set
in `.env` (values written without quotes: `.env` is read as a properties file, so quotes would become
part of the JDBC URL).

### Database layout used

| Database (Supabase project) | Purpose | Written by 5A |
|---|---|---|
| `postgres` | development API (`.env`) | **no** — checked before and after: 4 accounts, 4 audit events, unchanged |
| `mednet_test` (new) | `mvn -Ppostgres-it verify` | yes, reset each run |
| `mednet_e2e_test` (new) | live-API browser suites | yes, synthetic `@mednet.test` data only |

The `postgres` database already held MedNet V1–V15 (applied 2–9 October) and four accounts with
non-test addresses, which looks like an earlier deployment's data even though the project was described
as disposable; Phase 5A therefore wrote nothing there. Its Flyway history checksums match the repository
migrations for all 15 versions (compared read-only against `mednet_test`), so the development API can
start on it without a validation failure.

### Changes

- `PostgreSqlWorkflowIT` (new): runs all 26 `MedNetApplicationTests` workflow tests on PostgreSQL.
- `PostgreSqlTestDatabase` (new, shared with `PostgreSqlIntegrationIT`): reads `MEDNET_TEST_DATABASE_*`,
  refuses unless the database name ends in `_test`, the role is not a superuser and owns the database,
  then drops and recreates that database's `public` schema once per run. An earlier draft used
  `DROP OWNED BY CURRENT_USER`; it was replaced before it ever ran, because it also revokes the role's
  grants on shared objects and would have touched the `postgres` database's privileges through
  Supabase's shared `postgres` role.
- `web/e2e/support/live-backend.ts`: the stand-in email inbox can now write tokens to a PostgreSQL
  `_test` database through `psql` (`MEDNET_LIVE_PG_*`, password via the child environment); H2 path
  unchanged. Without it the email-token tests in `auth.live` would have skipped.
- No application code, migration or API contract changed. No compatibility defect was found.

### Results — H2 and PostgreSQL kept separate

| Check | Database | Result |
|---|---|---|
| `mvn test` | H2 | **49/49** |
| `PostgreSqlIntegrationIT` (migrations validate, version, entity round trip) | PostgreSQL 17.6 | **2/2** |
| `PostgreSqlWorkflowIT` (auth, roles, appointments, consultations, records/consent, lab review/return/release, admin, audit) | PostgreSQL 17.6 | **26/26** |
| API startup on an empty database | PostgreSQL 17.6 | 15 migrations applied in 37 s; healthy |
| `auth.live` (incl. email-token steps via `psql`) | PostgreSQL | **30/30** |
| `find-care.live` | PostgreSQL | **24/24** |
| `patient.live` | PostgreSQL | **33/33** |
| `provider.live` | PostgreSQL | **24/24** |
| `admin.live` | PostgreSQL | **27/27** |
| `laboratory.live` | PostgreSQL | **36/36** (e2e database holds RETURNED and RELEASED results) |
| Frontend lint and typecheck | — | pass (only `live-backend.ts` and one skip message changed) |
| Vitest, mocked Playwright, production build | — | not re-run: no application frontend file changed (4G results stand) |

**Latency, not behaviour:** the first live pass on PostgreSQL failed 14 tests, all at Playwright's
30-second hook limit or 5-second assertion wait right after a write. Supabase (us-west-2) costs about
200 ms per database round trip from this machine; a directory list takes about 1 s through the API. The
four affected suites were rerun unchanged with a scratchpad-only config (300-second test, 30-second
assertion waits) and passed completely. The API logged no errors. The workflow IT took 6 minutes against
15 seconds on H2.

### Remaining database risks

1. **Region latency:** a deployment must run the API in or near the database's region; at this latency
   appointment requests take several seconds.
2. **The `postgres` database's existing accounts:** confirm whether a deployment uses it before treating
   the project as disposable; rotate the database password (shared in chat on 2026-10-11).
3. **Not exercised:** concurrent-update behaviour under real contention, ordering ties on equal
   `created_at`, and pooler transaction mode (port 6543 is unsuitable; use 5432 session mode).
4. **Local PostgreSQL** remains unprovisioned (optional now); `docs/POSTGRESQL_SETUP.md` has the SQL.
5. Lists capped at 100 (carried from 4G).

### Files (5A)

- New: `src/test/java/com/mednet/PostgreSqlTestDatabase.java`, `PostgreSqlWorkflowIT.java`,
  `docs/POSTGRESQL_SETUP.md`.
- Changed: `src/test/java/com/mednet/PostgreSqlIntegrationIT.java`, `web/e2e/support/live-backend.ts`,
  `web/e2e/auth.live.spec.ts` (skip message), `web/e2e/README.md`, `README.md`, `docs/ADMIN_SETUP.md`,
  `docs/FRONTEND_API_HANDOFF.md`, `docs/FRONTEND_IMPLEMENTATION_PLAN.md`, this file.
- Local only (git-ignored): `.env` (Supabase datasource; previous local values kept as comments),
  `.env.test.local` (test database settings).

## Phase 4G log — preview operations workspaces and completion

### Assessment

- **Reference captured** (1440 and 390): collection provider, collection agent, customer support, financial
  officer, specimen lifecycle and permissions matrix, with every sub-menu. Every sub-view in the reference is
  the same generic template (list with search and filters + "Selected record" with Open workflow / View
  activity + a "Restricted" note); only the sample rows change per role.
- **Backend confirmed unchanged for these roles:** no collection provider, collection agent, support or
  financial officer role (`AdminWorkflowService.ACCOUNT_TYPES`), no pickup, custody, ticket or payment
  endpoint. Payments stay out of scope (`AGENTS.md`).
- **Existing preview convention:** the `(dev)` route group (`/foundation`, `/foundation/workspace/[id]`),
  404 in production. Reused for the gate pattern; the workspace registry already listed the four
  workspaces (`backend: "none"`).
- **Reuse:** `WorkspaceShell`, numbered navigation, `MetricCard`/`MetricGrid`, `DataTable`, `ActivityList`,
  `AuditEvent`, `DescriptionList`, `RestrictedNotice`, `Badge`, `Alert`, `TextArea`, `SearchInput`,
  `FictionalDataBadge`, `fictional()`, and the 4E/4F `record-layout` building blocks. New pieces are small
  extensions: `WorkspaceShell` `banner` slot, `PreviewAction`, `PreviewSubView`, `PreviewBanner`.

### Implementation mapping

| Reference | Route | Classification | Built from |
|---|---|---|---|
| Collection provider: Overview + 6 sub-views | `/preview/collection[/…]` | presentation-only | metrics, activity list, record panel, workload bars, exceptions, payouts card |
| Collection agent: Today + 3 sub-views | `/preview/collection-agent[/…]` | presentation-only | rules band, phone frame, step list, generic sub-view |
| Customer support: Overview + 5 sub-views | `/preview/support[/…]` | presentation-only | metrics, ticket list, conversation, disabled reply |
| Financial officer: Overview + 5 sub-views | `/preview/finance[/…]` | presentation-only | metrics, `DataTable`, transaction detail, recipients, audit events |
| Specimen lifecycle: Workflow map, Exception paths, Dependencies | `/preview/specimen-lifecycle[/…]` | presentation-only, annotated with MedNet support per stage | stage cards, exception cards, dependency list |
| Permissions matrix: matrix, Access principles, Audit requirements | `/preview/permissions-matrix[/…]` | presentation-only proposal + factual "enforced today" list | table, principle cards |
| Index | `/preview` | presentation-only | cards |

### Preview routing and access controls

- **Separate tree:** `app/(preview)/preview/*`, one layout with `previewsEnabled()`:
  - development and test: on;
  - production: **off (404)** unless `MEDNET_PREVIEW_WORKSPACES=enabled` is set as an explicit deployment
    decision. The layout is dynamic, so the flag is read per request (no rebuild). Verified on the real
    production build with `next start`: `/preview`, `/preview/finance`, `/preview/collection/pickup-requests`
    returned **404** by default and **200** with the flag; unknown sections 404 either way.
- **No roles, no sessions, no API:** preview pages read no session and import nothing from `@/lib/api`
  (enforced by a unit test that scans every preview source file for API, fetch, session and browser-storage
  use); Playwright records **zero `/api/` requests** across every preview view at all three widths and while
  using the controls. No account can be given a preview role; the registry marks them `preview: true` with
  `backendRole: null`, and `homePathForRole` never returns a preview path.
- **Persistent indicator:** a sticky "Preview only — Fictional data · This role does not exist in MedNet"
  banner above the top bar on every preview page (checked in view after scrolling), plus the top-bar badge.
- **Controls:** every action drawn in the reference is a `PreviewAction` — focusable, `aria-disabled`,
  announces "Preview only: no action was taken." Reply boxes are disabled. Row selection and search are
  local UI state.
- **No real data:** all values come from `features/preview/fixtures.ts`, wrapped in `fictional()`.

### Approved backend changes (§7, §8)

- **§7 Laboratory result read-back:** `GET /api/v1/partner/lab-results` (`LABORATORY` only). Returns results
  **authored by the caller on requests still assigned to the caller**, newest first, latest 100, as
  `LaboratoryResultDetails` (id, requestId, summary, status, createdAt, reviewedAt) — no patient or reviewer
  identifier. Read-only; no change to review or release. Uses the existing `author_account_id` column: no
  migration, no other authorization change. Audit action `laboratory_result.partner_accessed` (the first name
  exceeded the 40-character audit column; caught by the backend test).
  - Tests: pending → returned → released read-back; another laboratory sees an empty list; patient, doctor,
    super admin and home-care get 403; anonymous 401; no PATCH on the resource; a laboratory calling the
    doctor's review endpoint gets 403.
  - Frontend: the laboratory workspace now loads its submissions; order badges and timelines show "Awaiting
    doctor review", "Returned · correction needed" or "Result released" from the server; a returned result
    shows the return time and a "Corrected result" form; the Results page groups Returned / Awaiting review /
    Released. The 4F session-only memory was removed.
- **§8 Google sign-in:** `googleSignInDestination("LABORATORY")` → `/laboratory`; other roles unchanged
  (unit test covers all roles). **Not verified end-to-end:** no Google OAuth client is configured locally,
  so the Google flow itself has never run in this project's tests.

### Final Phase 4 integration review

| Area | Evidence | Result |
|---|---|---|
| Public site and directory | `public`, `fidelity`, `find-care` specs; `find-care.live` | pass |
| Authentication and account access | `auth` specs; `auth.live` (30/30, super admin at every width) | pass |
| Patient workspace | `patient` specs; `patient.live` 33/33 | pass |
| Doctor workspace | `provider` specs; `provider.live` 24/24 | pass |
| Administrator / Super administrator | `admin` specs; `admin.live` 27/27 | pass |
| Laboratory | `laboratory` specs; `laboratory.live` 36/36 | pass |
| Preview workspaces | `preview` spec 54/54 | pass |
| Role redirects | each workspace guard sends other roles home (mocked per workspace; live for patient, provider, admin, laboratory); `/account` forwards admin, provider and laboratory | pass |
| Session expiry | patient, doctor, admin and laboratory specs replace the page with "Your session has ended" and remove data | pass |
| Route protection | every real workspace checks the session, and the API refused every cross-role call tried live | pass |
| Design consistency | one shell, one token set, shared record layout across admin, laboratory and previews | pass |

**Gaps and defects found (for final stabilization, not fixed here):**

1. **Home-care partners have no workspace.** The API supports `HOME_CARE` (assigned queue, schedule, start,
   complete) but `HOME_CARE` sessions land on `/account`. The reference has no home-care design.
2. **Legacy CSS:** the pre-reference `.hero`, `.section-heading` etc. rules in `globals.css` appear unused;
   verify and remove.
3. **Google OAuth** redirects are unit-tested only.
4. **PostgreSQL:** all live runs used H2; run the PostgreSQL integration profile before deployment. Root cause of
   the rejected `.env` credentials found in 5A: the role was never created. Resolved in 5A on Supabase (see Phase 5A).
5. **Lists capped at 100** (laboratory read-back, admin queues, audit) with no pagination.
6. **Provider review queue** still calls the API once per patient.

### Test results (final run)

Mocked:

| Check | Result |
|---|---|
| ESLint | 0 problems |
| TypeScript | pass |
| Vitest | 149/149 (new: 3 preview, 1 laboratory, 1 workspace registry) |
| Playwright, all specs, 390/820/1440 | **600 passed, 0 failed, 15 skipped** (the long-standing skips); 54 new preview tests (18 per width) and 3 new laboratory tests (1 per width) |
| Production build | pass (15 `/preview` routes, dynamic) |
| Production gate (`next start`) | 404 by default; 200 only with `MEDNET_PREVIEW_WORKSPACES=enabled` |

Real API (local H2, synthetic accounts):

| Suite | Result |
|---|---|
| `laboratory.live` (now with server read-back after submit, return and release) | **36/36** |
| `admin.live` | 27/27 |
| `auth.live` | 30/30 |
| `find-care.live` | 24/24 |
| `patient.live` | 33/33 |
| `provider.live` | 24/24 |
| `mvn test` | 49/49 |

### Files (4G)

- Backend: `laboratory/api/LaboratoryResultController.java`, `laboratory/app/LaboratoryResultService.java`,
  `laboratory/data/LaboratoryResultRepository.java`, `auth/security/SecurityConfig.java`, tests
  `MedNetApplicationTests.java`, `SecurityConfigTests.java`; docs `ADMIN_SETUP.md`, `FRONTEND_API_HANDOFF.md`.
- New `web/src/features/preview/` (`access.ts`, `preview-shell.tsx`, `preview-action.tsx`, `components.tsx`,
  `fixtures.ts`, `views.tsx`, `preview.test.ts`); routes `app/(preview)/` (22 files).
- Changed: `components/layout/workspace-shell.tsx` (`banner`), `features/workspace/workspaces.ts` (preview
  paths, two reference workspaces, `preview` flag) and its test, `features/laboratory/*` (read-back).
- Tests: `e2e/preview.spec.ts`; updated `e2e/laboratory.spec.ts`, `e2e/support/laboratory-stub.ts`,
  `e2e/laboratory.live.spec.ts`.

## Phase 4F log — laboratory workspace

### Audit findings

- **No laboratory frontend existed.** `LABORATORY` sessions landed on `/account`.
- **Backend laboratory contract** (`PartnerServiceRequestController`, `LaboratoryResultController`,
  `PatientServiceRequestService`, `LaboratoryResultService`):

| Step | Who | Endpoint | Rule |
|---|---|---|---|
| Request a test | patient | `POST /lab-requests` `{testDescription}` | creates OPEN; admins notified |
| Assign | ADMIN/SUPER_ADMIN | `PATCH /admin/service-requests/{id}/assignment` | OPEN and unassigned; staff must be active, verified `LABORATORY` |
| See assigned work | laboratory | `GET /partner/service-requests?type=LABORATORY` | own assignments only; returns test text, status, dates. No patient identity |
| Start | laboratory | `PATCH /partner/service-requests/{id}/status` `IN_PROGRESS` | OPEN → IN_PROGRESS only; the lab can never resolve; patient notified |
| Submit result | laboratory | `POST /partner/service-requests/{id}/lab-result` `{summary ≤ 9,700}` | request IN_PROGRESS and no result already PENDING_REVIEW; result stored as **PENDING_REVIEW** |
| Review | doctor with care relationship **and** the patient's consent | `GET /patients/{id}/lab-results/pending`, `PATCH /lab-results/{id}/review` `RELEASE`/`RETURN` | RELEASE → result RELEASED, request RESOLVED, clinical record added, patient notified. RETURN → result RETURNED, request stays IN_PROGRESS, so the lab can submit a correction |
| Read | patient | `GET /patients/me/lab-results[/{id}]` | RELEASED only |

- **Difference from the brief's sequence:** the patient (not a doctor) creates the request, and any doctor
  with a care relationship and consent reviews it — there is no "ordering doctor". Release happens only
  through doctor review; there is no laboratory-side validation or release step.
- **Missing in the API (not built, not simulated):** a laboratory cannot read back its own submitted
  results or their review status (pending vs returned); no accept/reject of an order; no specimen
  collection, custody, receipt checks or rejection; no file upload; no test catalogue, urgency,
  turnaround, requesting-doctor or patient reference on an order; no laboratory organization (accounts
  are individual); no laboratory audit view; no pricing or payouts.
- **Patient and doctor sides** were already integrated (4C results list, 4D review); one patient wording
  fix (results are reviewed by "a doctor caring for you", not "the doctor who ordered it").

### Backend changes (approved scope, §8 and §9)

- **§8 Administrator restriction** (`AdminWorkflowController`, `AdminWorkflowService`): `PATCH
  /admin/accounts/{id}/status` now answers **403** when an `ADMIN` targets an `ADMIN` or `SUPER_ADMIN`
  account; checked before any change. `SUPER_ADMIN` powers are unchanged. The configured administrator
  still answers 409. Edit, role, verification and delete were already `SUPER_ADMIN`-only. Test
  `administratorCannotChangeTheStatusOfPeerOrSuperAdministratorAccounts`: suspend and reactivate both
  refused for both target types, accounts unchanged, administrators still suspend a patient, a super
  administrator still suspends an administrator.
- **§9 Assignment visibility** (`PatientServiceRequestService.AdminRequestSummary`): new nullable field
  `assignedStaffPublicId` (the assigned staff member's public account ID, one batched lookup per list).
  No name, email or internal ID; no change to existing fields. Contract test asserts absent before
  assignment, the public ID after, and that service, location and internal IDs stay hidden.
- No migration, no new endpoint, no other authorization change. Docs: `ADMIN_SETUP.md`,
  `FRONTEND_API_HANDOFF.md`.

### Completed

| Route | Content | Data |
|---|---|---|
| `/laboratory` | "Order operations": four counts, order queue (5), supported processing stages, "Submitting does not release a result" | assigned requests |
| `/laboratory/orders` | full queue (search, status filter) + order detail: requested test, dates, request timeline, Start processing, Submit for doctor review | assigned requests, status, lab-result |
| `/laboratory/results` | processing vs released-by-a-doctor, with the read-back limitation explained | assigned requests |
| `/laboratory/specimens` | **presentation-only**: future specimen steps and a disabled receipt checklist | none |
| `/laboratory/test-catalogue`, `/pricing-payouts`, `/audit-history` | **presentation-only**, Fictional data | none |
| `/laboratory/profile` | account email, type, permissions | session |

- **Statuses shown** are the backend's: OPEN "Not started", IN_PROGRESS "Processing", RESOLVED "Result
  released", CANCELLED "Cancelled". "Awaiting doctor review" appears only for a result submitted in this
  browser session or when the server answers that one is already pending — kept in memory, never in
  browser storage.
- **Request timeline**: Assigned (received time) → Processing started → Result submitted for doctor
  review → Released to the patient by a doctor (updated time). Only states the data proves.
- **Conflicts**: a 409 or 404 on start or submit shows the server's message and reloads the queue (e.g.
  an order cancelled elsewhere shows as cancelled).
- **Routing**: `homePathForRole("LABORATORY")` → `/laboratory`; `/account` forwards laboratory sessions.
  The backend Google handler still sends laboratories to `/account` (forwarded); changing it is a backend
  change not in this package's approval.
- **Admin UI updates**: no Suspend/Reactivate on administrator accounts for an `ADMIN` (with an
  explanation); the task queue shows "Assigned to LB-…" in rows and the record, and hides Assign and Mark
  in progress once assigned.
- **Shared code**: list + selected-record building blocks moved to `features/workspace/record-layout.tsx`
  (admin re-exports them unchanged).

### Real API results (local API on H2, synthetic accounts)

| Suite | Result |
|---|---|
| `laboratory.live.spec.ts` (new, 12 tests × 3 widths) | **36/36** |
| `admin.live.spec.ts` | 27/27 |
| `auth.live.spec.ts` | 30/30 |
| `find-care.live.spec.ts` | 24/24 |
| `patient.live.spec.ts` | 33/33 |
| `provider.live.spec.ts` | 24/24 |

Laboratory workflow, every stage through the acting role's real UI and checked from the other sessions:

1. Patient requests the test in the patient workspace (OPEN; no laboratory sees it yet).
2. Administrator assigns it in the task queue; the queue then shows `assignedStaffPublicId`, offers no
   second assignment, and the record never shows the test text.
3. Laboratory sign-in lands on `/laboratory`; the order appears without the patient's name or email.
4. Laboratory starts processing (patient sees IN_PROGRESS).
5. Laboratory submits a result: PENDING_REVIEW, request still IN_PROGRESS, a second submission is 409,
   and the patient's API and results page show nothing.
6. The doctor sees it under the patient's "Results to review".
7. The doctor **returns** it; the patient still sees nothing; the laboratory submits a **corrected**
   result; the doctor **releases** the correction.
8. The patient sees only the corrected, released result in the records page; the returned draft never
   appears; the order shows as released in the laboratory and RESOLVED to administrators; late
   submissions are 409.
9. Unreleased results were checked invisible to the patient after steps 5, 7 (return) and 7 (correction).
10. Refusals: another laboratory (not in queue; start/submit 404), a home-care account (403), the lab
    resolving (409), the patient's pending-results route (403), administrators and laboratories reading
    patient results (403), a doctor without a care relationship (403), anonymous (401); nothing changed.

Also live: an `ADMIN` gets 403 suspending a peer administrator (409 for the configured super admin) and
sees no status buttons; a `SUPER_ADMIN` still can; laboratory sign-out removes the orders.

### Validation (final run, mocked)

| Check | Result |
|---|---|
| ESLint | 0 problems |
| TypeScript | pass |
| Vitest | 144/144 (4 new laboratory, 1 admin, 1 account-forwarding) |
| Playwright, all specs, 390/820/1440 | **543 passed, 0 failed, 15 skipped** (the long-standing skips). New: 63 laboratory tests (21 per width: 8 views × WCAG/overflow/targets; start, submit, duplicate, stale-order, no-action, empty, access, expiry, sign-out) and 9 admin tests (3 per width) |
| Production build | pass (8 `/laboratory` routes) |
| `mvn test` | 49/49 |

One mocked test changed for the new routing: the patient-workspace "other roles" redirect now uses
`HOME_CARE` (a `LABORATORY` session goes to `/laboratory`).

### Files (4F)

- Backend: `admin/api/AdminWorkflowController.java`, `admin/app/AdminWorkflowService.java`,
  `service/app/PatientServiceRequestService.java`, `MedNetApplicationTests.java`; docs `ADMIN_SETUP.md`,
  `FRONTEND_API_HANDOFF.md`.
- New `web/src/features/laboratory/`: `lab-api.ts`, `laboratory-workspace.tsx`, `status.ts`, `orders.tsx`,
  `views.tsx`, `laboratory.test.ts`; routes `app/laboratory/` (layout + 8 pages);
  `features/workspace/record-layout.tsx`.
- Changed: `features/admin/{frame,accounts,tasks,overview,permissions,admin-api,admin.test}`,
  `features/workspace/workspaces.ts`, `features/auth/account-panel.tsx` (+ tests),
  `features/patient/records.tsx` (wording).
- Tests: `e2e/laboratory.spec.ts`, `e2e/support/laboratory-stub.ts`, `e2e/laboratory.live.spec.ts`; updated
  `e2e/admin.spec.ts`, `e2e/support/admin-stub.ts`, `e2e/admin.live.spec.ts`, `e2e/patient.spec.ts`.

### Open points from 4F

- **Proposed (needs approval):** a read-only `GET /partner/service-requests/{id}/lab-results` for the
  assigned laboratory (status and dates of its own submissions), so returned results are visible to the
  laboratory and the timeline survives a reload.
- **Proposed:** Google sign-in `LABORATORY → /laboratory` (one line in `googleSignInDestination`).
- **Future requirements (in the design inventory, not simulated):** order accept/reject with reason,
  specimen collection/custody/receipt/rejection, result documents, laboratory-side validation, test
  catalogue, urgency and turnaround, laboratory organizations, laboratory audit view, pricing/payouts.
- Policy questions from the reference remain open: result-review deadline and unavailable-doctor
  fallback, critical-value escalation, recollection responsibility.

## Phase 4E log — administrative workspaces

### Audit findings

- **Legacy console:** `components/admin-portal.tsx` (1,594 lines) plus about 170 lines of `.admin-*` CSS in
  `globals.css`. One page, five tabs (Overview, Providers, User management, Requests, Audit), one shared
  view for both roles, `window.confirm` for deletion only, and no confirmation for suspension or provider
  decisions.
- **Backend permissions** (`SecurityConfig`, `AdminWorkflowController`, `AdminServiceRequestController`):
  - `/api/v1/admin/**` admits `ADMIN` and `SUPER_ADMIN` only (401 anonymous, 403 other roles).
  - Both roles: overview, provider applications (list, manual intake, status), account list/search/
    create (patient, provider, laboratory, home-care types only) and suspend/reactivate, operational
    references, patient service-request queue (status, assignment), appointment list, audit trail.
  - `SUPER_ADMIN` only: `PUT /accounts/{id}`, `PATCH /accounts/{id}/role`, `PATCH /accounts/{id}/verification`,
    `DELETE /accounts/{id}`. The configured administrator cannot be changed through the API (409).
- **Endpoints the legacy console did not use:** `GET /admin/appointments`, `GET /admin/service-requests`,
  `PATCH /admin/service-requests/{id}/status` and `/assignment`. All are now integrated.
- **Figma reference:** both overviews are fully designed; every sub-view is one generic template
  ("SUBMENU" list with search and status filter, plus a "SELECTED RECORD" panel with Open workflow /
  View activity and a "Restricted" note) whose content does not change between menu items.

### Super-admin login bug: root cause and fix

- **Symptom:** the configured super admin (`MEDNET_ADMIN_EMAIL`/`MEDNET_ADMIN_PASSWORD`) could sign in once
  per API process; every later attempt returned 401.
- **Root cause (session handling, not credentials, account status, role or redirects):**
  `SecurityConfig.userDetailsService` built one `User` for the configured administrator and returned that
  same instance on every lookup. After a successful sign-in, Spring Security's `ProviderManager` erases
  the credentials of the authenticated principal (`CredentialsContainer.eraseCredentials()`), which set
  the shared instance's password hash to null. Database accounts were unaffected because they build a
  new `User` per lookup.
- **Reproduced first** with a new regression test (`configuredAdministratorCanSignInRepeatedly`: the
  second sign-in returned 401 before the fix).
- **Fix (narrowest possible):** keep the encoded hash and build a new `User` per lookup. The password is
  still checked with BCrypt, the role is still `SUPER_ADMIN`, nothing else changed.
- **Tests:** three repeated sign-ins succeed and a wrong password is still 401
  (`MedNetApplicationTests`); a unit test confirms erasing one principal's credentials does not affect
  the next lookup (`SecurityConfigTests`). Live: the super admin signed in repeatedly on one API
  process during the runs below, and `auth.live.spec.ts` no longer skips the admin case.

### Approved 4D follow-ups

- **A. Google sign-in redirect:** the success handler's role switch moved into
  `SecurityConfig.googleSignInDestination(role)` and gained `PROVIDER → /provider`. Other roles are
  unchanged. Unit test covers every role. The Google flow itself was not run (no OAuth client locally).
- **B. Applicant link:** the patient workspace asks `GET /providers/me/application` once; when it answers
  200 the Account menu shows "Provider application status" (→ `/provider`, the read-only applicant
  status page). A 404 or any failure hides the link. Applicants still get no doctor workspace
  navigation or data (mocked tests).
- **C. Patient-initiated rescheduling:** not implemented. Recorded as a separate patient-workspace
  enhancement (see open points).

### Completed

One URL tree, `/admin/*`, with one guard (`app/admin/layout.tsx` → `AdminWorkspace`). The session role
chooses the menu: `SUPER_ADMIN` gets the super-administrator workspace, `ADMIN` the administrator
workspace. A route that is not in the role's menu shows "This page is not part of the … workspace"
(navigation only; the API is the boundary).

| Route | Super administrator | Administrator | Data |
|---|---|---|---|
| `/admin` | Platform control centre | Daily operations | overview counts, pending applications, audit, unverified count |
| `/admin/users` | Users and roles (full account management) | not in menu | `/admin/accounts` |
| `/admin/providers` | Providers (decisions) | not in menu (same panel under Onboarding) | `/admin/providers` |
| `/admin/verification` | Unverified email addresses | not in menu | `/admin/accounts?emailVerified=false` |
| `/admin/security` | Audit trail + access model | not in menu (audit under Reports) | `/admin/audit` |
| `/admin/configuration` | Read-only module status and API health | not in menu | `/admin/overview` |
| `/admin/tasks` | Task queue (07, added) | Task queue | `/admin/service-requests`, `/admin/requests` |
| `/admin/appointments` | Appointments (08, added) | Appointments | `/admin/appointments` |
| `/admin/onboarding` | not in menu | Provider applications + Accounts tabs | as above |
| `/admin/escalations` | not in menu | presentation-only (Fictional data) | none |
| `/admin/reports` | not in menu | live counts + audit trail | `/admin/overview`, `/admin/audit` |

- **Account management** (`accounts.tsx`, shared): search, status/type/verification filters, applied-filter
  chips, 25/50/100 rows, pagination, result count. Selected-record panel with Suspend/Reactivate (both
  roles) and Edit details, Mark email verified/not verified, Delete (SUPER_ADMIN only). Every action asks
  for confirmation; your own account has no actions. Create account: name, email, the four creatable
  types only, generated (default, 32 characters, shown once with Copy) or manual password with
  12–72 character validation. Edit warns when an account type grants administrative access.
- **Provider applications** (`providers.tsx`, shared): status filter (default "Awaiting review"), local
  search, manual intake dialog, record panel with the submitted credential reference "as submitted",
  Approve/Decline (pending) and Suspend (approved), each confirmed. Wording never says "verified".
- **Task queue** (`tasks.tsx`): home-care and laboratory request queues (reference, status, date only),
  Mark in progress, Cancel (confirmed) and Assign to matching active, verified staff; operational
  references with Add, Start and Resolve (legacy behaviour).
- **Appointments, audit trail, configuration, reports** (`operations.tsx`): shortened internal references,
  Liberia time, no names or clinical content.
- **Shared frame** (`frame.tsx`): page heading, list card, selected-record panel (focus moves to its
  heading when a record is chosen, so the panel is reachable when it stacks below the list on small
  screens), selectable rows (`aria-pressed`).
- **Removed:** `components/admin-portal.tsx` and the `.admin-*` CSS block, after the new workspace passed
  the mocked and live checks below. Every legacy capability is preserved (table below).

| Legacy capability | Now |
|---|---|
| Overview: API status, uptime, counts, module status | overview metrics, Configuration (SA), Reports (ADMIN) |
| Provider intake, approve, reject, suspend | Providers (SA), Onboarding (ADMIN), now confirmed |
| Account search, filters, page size, first/prev/next/last | Users and roles / Onboarding › Accounts (page window replaces first/last) |
| Create account, generated password shown once, copy | same, generated is now the default |
| Suspend/reactivate (both roles), edit/verify/delete (SA) | same, all confirmed |
| Operational references: add, start, resolve | Task queue › Operational references |
| Audit trail | Security (SA), Reports (ADMIN) |
| Sign out, account settings | Account menu |

### Authorization and security checks

- Server enforcement verified live (`admin.live.spec.ts` test 6): an `ADMIN` gets 403 for edit, role,
  verification and delete, and 403 when promoting itself; 400 when creating a `SUPER_ADMIN`; a patient
  gets 403 and an anonymous caller 401 on overview, accounts, audit and providers; nothing changes.
- UI: an administrator never sees edit/verify/delete controls; super-administrator routes show a denial
  state; patients and providers opening `/admin` are sent to their own workspace; sign-out and session
  expiry remove all administrative data from the page.
- **Finding (backend, not changed):** `PATCH /admin/accounts/{id}/status` has no role check beyond
  ADMIN/SUPER_ADMIN, so an `ADMIN` can suspend another `ADMIN` or a database `SUPER_ADMIN` (only the
  configured administrator is protected). The UI keeps the legacy behaviour. Restricting it is an
  authorization change and needs your approval.

### Real API results (local API on H2, synthetic accounts)

| Suite | Result |
|---|---|
| `admin.live.spec.ts` (new, 9 flows × 3 widths) | **27/27** |
| `auth.live.spec.ts` | **30/30** (super-admin case now runs at every width) |
| `find-care.live.spec.ts` | 24/24 |
| `patient.live.spec.ts` | 33/33 |
| `provider.live.spec.ts` | 24/24 |

Admin flows: super-admin repeated sign-in → `/admin`; provider intake and approval, then the provider signs
in as `PROVIDER` and appears in the directory; account create → unverified sign-in refused → verify →
sign-in works → suspend → refused → reactivate → edit → delete, each in the audit trail; deleting an
account with care data is refused with the API's reason; administrator workspace limits; server refusals
(above); patient redirect; home-care request assigned by an administrator appears in the partner's queue
without the administrator seeing the requested service or address, and a second assignment is refused;
sign-out removes access.

### Validation (final run, mocked)

| Check | Result |
|---|---|
| ESLint | 0 problems |
| TypeScript (`next typegen && tsc`) | pass |
| Vitest | 138/138 (10 new admin tests) |
| Playwright, all specs, 390/820/1440 | **471 passed, 0 failed, 15 skipped** (the long-standing skips). Includes 90 new admin tests (15 views × WCAG/overflow/target checks, flows, negative cases) and 6 new patient tests; the 4C notification-link check still passes |
| Production build | pass (11 `/admin` routes) |
| `mvn test` | 48/48 (3 new) |

Visual checks at 1440, 820 and 390 for both roles: no horizontal scroll. Fixed during review: the account
table wrapped "Open"/"Active" mid-word beside the record panel (columns merged, badges no-wrap); the
filter row now puts search on its own line; an administrator could open `/admin/users` because every
`/admin/*` path prefix-matched the Overview link (route membership now ignores Overview); Search with
unchanged filters now refreshes the list.

### Files (4E)

- Backend: `auth/security/SecurityConfig.java` (fresh principal per lookup; `googleSignInDestination`),
  `MedNetApplicationTests.java`, `auth/security/SecurityConfigTests.java`; docs `ADMIN_SETUP.md`,
  `FRONTEND_API_HANDOFF.md`.
- New `web/src/features/admin/`: `admin-api.ts`, `permissions.ts`, `admin-workspace.tsx`, `frame.tsx`,
  `accounts.tsx`, `providers.tsx`, `tasks.tsx`, `operations.tsx`, `overview.tsx`, `views.tsx`, `admin.test.tsx`.
- Routes: `app/admin/layout.tsx`, `page.tsx` and 10 sub-routes.
- Changed: `features/workspace/workspaces.ts` (menus), `components/workspace/workspace-sections.tsx`
  (`PermissionBoundary` middle label), `features/patient/patient-workspace.tsx` (applicant link),
  `app/globals.css` (legacy block removed).
- Deleted: `components/admin-portal.tsx`.
- Tests: `e2e/admin.spec.ts`, `e2e/support/admin-stub.ts`, `e2e/admin.live.spec.ts`; updated
  `e2e/patient.spec.ts`, `e2e/support/patient-stub.ts`, `e2e/auth.live.spec.ts`.

### Open points from 4E

- **Decision needed:** restrict account suspension of `ADMIN`/`SUPER_ADMIN` accounts to `SUPER_ADMIN`
  (backend authorization change).
- **Proposed additive field:** `assignedStaffAccountId` on `AdminRequestSummary`, so the queue can show
  who holds a request instead of learning it from a 409.
- **Not available in the API (shown as text or omitted):** security events and sign-in history,
  configurable settings and integrations, configuration history, audit export, escalations, report
  export, organisation records for laboratories/collection providers, admin notifications in the top bar.
- **Patient-initiated rescheduling** (approved in principle): separate patient-workspace enhancement using
  `REQUEST_RESCHEDULE`, the provider's open slots and the existing provider response flow.
- Google sign-in redirects were unit-tested only; run once with a real OAuth client before release.

## Phase 4D log — doctor/provider workspace

### Audit findings

- **No provider frontend existed.** Providers landed on `/account`.
- **Roles at sign-in:** the backend grants the `PROVIDER` role only when the account has an **APPROVED**
  provider application (`SecurityConfig` user lookup). Otherwise it grants `PATIENT`, even for a
  provider account type. So pending, rejected and suspended applicants sign in as patients, and a
  session keeps its role until it ends.
- **Availability API:** publish one slot (`POST /providers/me/availability`; no past times, no
  overlaps), list open future slots (the public `GET /providers/{id}/availability`), and close an open
  slot (`DELETE`; 409 once reserved). There is no edit, no recurrence, and no list of the provider's
  closed or reserved slots; reserved times are shown from appointments.
- **Appointment state machine** (`AppointmentService`):
  - Provider-only actions: ACCEPT and DECLINE (PENDING), COMPLETE (CONFIRMED after it ends).
  - Either participant: CANCEL (before start) and REQUEST_RESCHEDULE (CONFIRMED, before start, using
    one of the provider's own open slots).
  - ACCEPT_RESCHEDULE and DECLINE_RESCHEDULE belong to the other participant only.
- **Consultations:** either participant can open one on a CONFIRMED TEXT appointment (idempotent;
  notifies the other side). Only the provider can end it.
- **Clinical data:** records, vitals and pending lab results need a care relationship (CONFIRMED,
  COMPLETED or RESCHEDULE_REQUESTED) **and** the patient's active consent. Any such provider may
  release or return a pending result; releasing also creates a `LAB_RESULT` record. There is no
  provider-wide results queue, and no laboratory ordering or prescribing API.
- **Messaging:** only patients can open a conversation, and only with a care relationship. Providers
  reply.
- **The backend already supports patient-initiated rescheduling.** The existing backend test exercises
  a patient REQUEST_RESCHEDULE. As instructed, the patient UI still does not offer it; it is now
  technically possible with `providerId` (below) and needs your decision.

### Backend change (approved scope, §9)

`AppointmentDetails` gained three trailing fields, derived from data that already exists:

- `providerId` (`appointment.providerApplicationId`)
- `providerSpecialty` (the provider record, which was already loaded)
- `rescheduleRequestedBy`: `PATIENT` or `PROVIDER` while RESCHEDULE_REQUESTED, otherwise `null`. It is
  derived by comparing the stored requester email with the patient's email; the email itself is never
  exposed.

There is no migration, no authorization change and no new endpoint. The change is additive JSON, so
existing clients are unaffected.

- Tests: `MedNetApplicationTests.appointmentSlotsRequireProviderConfirmationAndProtectBothParticipants`
  now asserts all three fields, including `PATIENT` after a patient's reschedule request and `null`
  once it is accepted.
- An unrelated fragile assertion was fixed in `guardedCareWorkflows…`: the test compared a
  nanosecond `Instant.now()` against the database's microsecond value and failed intermittently on
  Windows. It is now truncated to seconds, as the appointment test already does. This is a test-only
  change.
- `mvn test`: **45/45**.
- Docs: `docs/FRONTEND_API_HANDOFF.md` and `docs/backend-api-plan.md`.
- **Patient side:** the care team (sharing and messages) now uses `providerId` instead of name
  matching, the overview shows the real specialty, and a patient-initiated pending change reads
  "Waiting for the doctor" without Accept/Decline.

### Completed

- **Frame** (`app/provider/layout.tsx` → `ProviderWorkspace`): the doctor sidebar from the registry,
  an Account menu, and a top bar with "Good morning, Dr. {surname}".
  - Signed out → sign in, returning to the same path.
  - Session expiry → the shared "Your session has ended" screen (now `features/workspace/access-state.tsx`).
  - A patient with no application → `/patient`.
  - **Applicants** (PATIENT role with an application) see their application status on a standalone page
    (no workspace shell). If it was approved after sign-in, they are asked to sign in again.
  - A provider session whose application is no longer APPROVED sees only its status (profile still
    available).
- **Routing:** `homePathForRole("PROVIDER")` → `/provider`. `/account` forwards providers to
  `/provider`, which covers Google sign-in: the backend success handler still sends providers to
  `/account` and was **not changed**.
- **Pages (11 routes):**

  | Route | What it does (all live) |
  |---|---|
  | `/provider` | today's practice band with Liberia times; tiles for appointments today, requests to answer, results to review and unread notifications; today's schedule; clinical attention (pending results, patient-requested times); requests; clinical tools (only real actions); notifications (mark read, link to the appointment or consultation); earnings card labelled **Fictional data** |
  | `/provider/schedule` | publish a single open time (date, Liberia start time, length, text or in person), with overlap and past-time errors from the API; open times grouped by day with Close (409 for a requested time); reserved times read-only |
  | `/provider/appointments` | tabs Requests / Upcoming / Completed and closed. Actions shown only when the backend allows them: Confirm, Decline, Propose new time (choose an open slot), Accept or keep a patient-requested time, Cancel, Mark completed, Text consultation, Details drawer. 409s (stale state) show the API message and refresh the list |
  | `/provider/appointments/consultation` | open on explicit press, thread, send, refresh, End (confirm); read-only once ended; 404 for another provider's consultation |
  | `/provider/patients`, `/provider/patients/[id]` | patients from the provider's appointments; only care relationships open. Detail tabs: Records (paginated; add record; add a correction via `amendsRecordId`), Results to review (release or return, confirmed), Vitals, Appointments. A 403 shows *why* (no consent, or no care relationship) and hides the forms |
  | `/provider/clinical-work` | results waiting for review across care patients (one call per patient, up to 25), patients who have not shared records, and the unavailable tools (drafts, ordering, prescribing) stated plainly |
  | `/provider/messages` | conversations patients opened, with names resolved from the provider's appointments; reply |
  | `/provider/earnings` | presentation-only, labelled, states that there are no payments or payouts |
  | `/provider/help` | factual topics linking to real features; no invented support channel |
  | `/provider/profile` | application details (name, specialty, the credential reference *as submitted*, status **Approved**, dates) and an application form when there is none or it was rejected |

- **Shared code** (no duplicate implementations):
  - `features/workspace/use-resource.ts` (moved from patient)
  - `states.tsx` (loading and error rows)
  - `access-state.tsx` (signed out, expired, error, loading)
  - `message-thread.tsx`, used by patient messages, the patient consultation, provider messages and
    the provider consultation
  - `ViewBand` takes an eyebrow
- **Wording:** "Approved" everywhere; "verified" never appears (asserted in tests). There is no video
  or voice, and no prescribing or lab ordering.

### Patient–provider workflows verified against the real API

The setup was a throwaway H2 database, the API on port 8081 with the Phase 4D backend change, and
`next dev` on 3300 with `MEDNET_API_URL`. Accounts were synthetic and seeded only through the admin
and provider endpoints.

`e2e/provider.live.spec.ts`: **24/24** (8 flows × 390/820/1440):

| Brief §11 | Result |
|---|---|
| Provider sign-in | password sign-in → `/provider`, greeting "Dr. {surname}"; `/account` → `/provider` |
| 1–2 Request → provider receives | patient books in `/patient/find-a-doctor`; two more requests via API; provider's Requests tab shows 3; appointments carry `providerId` and `providerSpecialty` |
| 3–5 Accept / decline / propose → patient responds → both agree | provider confirms one and declines one (patient API: CONFIRMED, DECLINED). Provider proposes the last open slot (API: `rescheduleRequestedBy: PROVIDER`). The patient accepts in the patient workspace. Patient and provider APIs return the same status and new start time, and the provider UI shows Confirmed |
| 6 Text consultation | provider opens and writes; the patient joins the same consultation in the patient workspace and replies; another patient gets 404; provider ends it; a later patient message gets 409 |
| 7 Record sharing | before consent the provider sees "has not allowed you"; after the patient grants consent the provider adds a record in the UI and the patient's API returns it with the doctor as author; a pending-only patient → 403 and is not openable; after revoking → 403 |
| 8 Messaging | a patient without a care relationship cannot open a conversation (403); the provider replies in the UI and the patient's API returns both messages; another patient → 404 |
| Availability | publish in the UI → appears in the public directory; overlap → 409 shown; close → removed from the directory |
| Pending applicant | the session role is PATIENT; `/provider/schedule` shows "Under review" with no workspace navigation; the applicant cannot read the provider's appointment or patient records |

Regression on the same API: patient live **33/33**, find-care **24/24**, auth **27/27** (super-admin
case excluded; known backend defect).

Not verified live: the laboratory release flow (it needs a laboratory account to submit a result; it is
covered by stubbed tests). The UI uses the same endpoints the backend test suite covers.

### Validation (final run)

| Check | Result |
|---|---|
| Resources | 3.8–4.6 GB free; one API (`-Xmx768m`) and one `next dev` at a time; Playwright at 2 workers (1 for live); servers stopped afterwards |
| `npm run lint`, `npm run typecheck` | pass |
| `npm test` (Vitest, mocked) | **128/128** (13 files). New `provider.test.tsx` (9): needs-response rules, allowed transitions for each state, tab grouping, today's schedule in Liberia time, patients from appointments (care relationship only for CONFIRMED/COMPLETED/RESCHEDULE_REQUESTED), the Dr. greeting, Liberia time to instant, never "verified". Plus the `/account` provider forward and the updated care team |
| `npm run test:e2e` (mocked, 2 workers, 390/820/1440) | **375 passed, 0 failed, 15 skipped** (Phase 3 skips). New `e2e/provider.spec.ts` (38 per width): all 15 provider views fit, one H1, ≥40px targets, axe **0 violations**; confirm, decline, propose (+ axe on the dialog), accept a patient's time, complete, publish, overlap, close, reserved-close 409, consultation open/send/end, record and correction, release a result, no-consent and request-only patients, reply, application states (provider session pending or suspended; applicant pending; approved after sign-in; none → apply; rejected → re-apply), patient redirect, session expiry, empty states. The Phase 4C notification-link check (`/patient` at 820px) passes |
| Live API (real backend) | provider **24/24**, patient **33/33**, find-care **24/24**, auth **27/27** (super-admin excluded) |
| `mvn test` | **45/45** |
| `npm run build` | pass; 11 `/provider` routes |
| Visual review | overview, appointments, schedule and patient at 390/820/1440, compared with the reference doctor dashboard. Fixed: overview row gaps (independent columns), earnings tiles full width in 4 columns as in the reference, clinical tool tiles. Also found that `sm:` classes have no effect (the project's breakpoints are 768/1200/1440); all provider uses changed to `md:` |

### Files (4D)

- Backend: `src/main/java/com/mednet/appointment/app/AppointmentService.java`;
  `src/test/java/com/mednet/MedNetApplicationTests.java`; docs `FRONTEND_API_HANDOFF.md`,
  `backend-api-plan.md`
- `web/src/app/provider/**` (layout and 11 pages)
- `web/src/features/provider/`: `provider-api.ts`, `provider-workspace.tsx`, `application.tsx`, `overview.tsx`,
  `appointments.tsx`, `schedule.tsx`, `consultation.tsx`, `patients.tsx`, `clinical-work.tsx`, `messages.tsx`,
  `presentation-views.tsx`, `fictional.ts`, `tabs.ts`, `provider.test.tsx`
- `web/src/features/workspace/`: `use-resource.ts` (moved), `states.tsx`, `access-state.tsx`, `message-thread.tsx`
- Changed: `features/workspace/workspaces.ts` (`homePathForRole`), `features/auth/account-panel.tsx`, and the
  patient `care-team.ts`, `patient-api.ts`, `appointments.tsx`, `overview.tsx`, `messages.tsx`,
  `consultation.tsx`, `patient-workspace.tsx`, `records.tsx`, `view-frame.tsx`, `format.ts` (LAB_RESULT label)
- Tests: `web/e2e/provider.spec.ts`, `web/e2e/support/provider-stub.ts`, `web/e2e/provider.live.spec.ts`;
  updated `patient.spec.ts`, `patient.live.spec.ts`, `support/patient-stub.ts`, `destination.test.ts`,
  `workspaces.test.ts`, `auth-panels.test.tsx`, `patient.test.tsx`

### Open points from 4D

- **Patient-initiated rescheduling:** supported by the backend; the patient UI can now offer it
  (`providerId` gives the open times). It needs your decision.
- **Google sign-in redirect:** the backend handler still sends providers to `/account`, which forwards
  to `/provider`. Changing `SecurityConfig` was left out to keep backend changes inside the approved
  scope.
- **Applicants and roles:** pending, rejected and suspended applicants sign in as patients by backend
  design. Their status is visible at `/provider`, but nothing links there from the patient workspace
  yet. A link would be a small 4C follow-up if you want it.
- **No provider-wide results queue:** Clinical work calls the API once per care patient (capped at
  25). A provider-scoped pending-results endpoint would remove that (proposed, not implemented).
- **Profile changes:** display name and specialty can only change through an administrator; there is
  no provider-side edit API.
- **Laboratory release** was not exercised live (it needs a laboratory partner flow); that belongs to
  4F.

## Phase 4C log — patient workspace

### Completed

- **Workspace frame:** every `/patient` route shares `app/patient/layout.tsx` → `PatientWorkspace`.
  - It combines a session guard, a profile context and `WorkspaceShell` with the reference sidebar.
  - The guard is UX only. Spring authorises every call (`hasRole('PATIENT')` plus ownership).
  - Signed out → sign in, returning to the same path and query. Other roles → their home
    (`homePathForRole`, unchanged). API down → error with retry.
  - An Account menu (profile, sign out) replaces the old sign-out button. Layout and deviations are
    in `DESIGN_SYSTEM.md` §15.
- **Eight routes,** all on `lib/api` (`features/patient/patient-api.ts`, typed from the Spring records):

  | Route | Live data and actions |
  |---|---|
  | `/patient` | next appointment, "For you" (incomplete profile, proposed new time), recent records and released results, request summary, notifications with mark-as-read; payments card labelled fictional |
  | `/patient/find-a-doctor` | the 4B discovery and booking view inside the workspace (`FindCare` gained `basePath`) |
  | `/patient/appointments` | upcoming and past; cancel (until start); accept or decline a proposed time; home-care tab: list, request, cancel (OPEN only) |
  | `/patient/messages` | conversations, start one with a care-team doctor, thread, send, refresh (not real time) |
  | `/patient/records` | tabs: records (paginated, expandable); laboratory (released results plus lab requests: request, cancel); medication reminders (add, record today's dose, stop); vitals (record, list); sharing (allow or withdraw record access per doctor) |
  | `/patient/payments` | presentation-only, `FictionalDataBadge`, states that MedNet takes no payments |
  | `/patient/help` | factual topics linking to real features; support team stated as not available |
  | `/patient/profile` | same contract and limits as before, plus client validation and the read-only sign-in email |

- **Confirmations:** every destructive or consent action uses `ConfirmDialog`, with focus starting on
  Cancel.
- **Validation:** forms validate against the backend limits before sending and focus the first
  invalid field. API messages are shown as returned.
- **Removed:** `components/patient-dashboard.tsx`, `components/patient-profile.tsx`, and 20 legacy
  `.patient-*` CSS rules (`globals.css` is now 362 lines).
- **Small shared changes:**
  - `AppointmentSummary` takes `modeIcon`, so text consultations no longer show a camera.
  - `MedicalRecordList` gained a `document` kind for other record categories.
  - The booking drawer's success link now reads "View my appointments" → `/patient/appointments`
    (was "Go to my dashboard").

### Backend gaps found (not changed; backend changes need authorisation)

- **`AppointmentDetails` has no provider id or specialty**, and doesn't say who requested a
  reschedule. As a result:
  - Patients cannot propose a new time from the UI, because their provider's open times can't be
    looked up. They can cancel and request again.
  - Record sharing and new conversations find the patient's doctors by matching the appointment's
    provider name against the directory, plus the ids already known from consents and conversations
    (`features/patient/care-team.ts`). The API still enforces the care relationship (403 otherwise).
  - If a patient-initiated reschedule exists (API only), "Accept" returns 403. This is shown as "Only
    the doctor can respond to a change you proposed."
  - Suggested fix: add `providerId`, `specialty` and `rescheduleRequestedBy` (`PATIENT`/`PROVIDER`) to
    `AppointmentDetails`.
- Appointment lists are ordered by `createdAt` with a 100-item page cap. The UI shows the 100 most
  recent requests and says so when there are more.
- Message threads are oldest first. The UI loads the newest page and notes when earlier messages are
  hidden.

### Integration testing against a running backend

The setup was as in 4B: the real API on port 8081 with a throwaway H2 database, `next dev` with
`MEDNET_API_URL`, and data seeded through the real admin and provider endpoints. The patient acts only
through the UI. The doctor's side (confirm, propose a new time, add a record, reply) uses the doctor's
own API session, and each result is checked back against the API.

`e2e/patient.live.spec.ts`: **27 passed** (9 flows × 390/820/1440).

| Flow | Result |
|---|---|
| Sign-in return | `/patient/records?tab=vitals` signed out → sign in → back on the same URL |
| Profile first | "Complete your patient profile" action → saved via PUT; greeting changes to "Good …, Ama" |
| Book in workspace | `/patient/find-a-doctor?provider=` → request sent → overview shows it as Pending |
| Proposed time | doctor ACCEPT + REQUEST_RESCHEDULE → "Review a proposed new time" → accepted; API `CONFIRMED`, proposed slot reserved |
| Cancel | pending request cancelled; API `CANCELLED` |
| Sharing | only the doctor with a confirmed appointment is listed (not the pending/cancelled one); consent `GRANTED`; the doctor then adds a record and it appears under Records |
| Messages | conversation started, message received by the doctor, reply appears on Refresh |
| Requests and notes | lab request `OPEN`; medication reminder (Africa/Monrovia, ACTIVE); vital 72.5 kg `PATIENT_REPORTED`; home-care request sent then `CANCELLED` |
| Notifications | mark as read lowers `/notifications/unread-count` by 1 |
| Wrong role | a provider session is sent to `/account`; the API returns 403 for patient data |

`find-care.live.spec.ts` was re-run after the `basePath` change: **24 passed**.

A defect found by the live run was fixed: tab panels were all mounted on page load, so a record a
doctor added afterwards did not appear when the patient switched tabs. Only the active panel is now
mounted, and it loads fresh data when opened.

### Validation (final run)

| Check | Result |
|---|---|
| `npm run lint`, `npm run typecheck` | pass |
| `npm test` (Vitest) | **115/115** (11 files). New `patient.test.tsx` (8): greeting and date in Liberia time, durations, record labels, next/upcoming/past ordering, care-team matching (only CONFIRMED/COMPLETED/RESCHEDULE_REQUESTED names, plus consent and conversation ids), recent-activity merge |
| `npm run test:e2e` (390/820/1440) | **243 passed, 15 skipped** (Phase 3 skips). New `e2e/patient.spec.ts` (28 per width, stubbed API): all 13 views fit with one H1, ≥40px targets and axe WCAG A/AA **0 violations**; signed-out, wrong-role and API-down states; empty states; cancel, accept proposed time, home-care cancel and validation, lab request, consent, message, profile validation then save, sign-out, workspace search path, each checking the request body and CSRF header |
| Live API | patient **27 passed**; find-care **24 passed** |
| `npm run build` | pass; `/patient`, `/patient/help`, `/patient/payments` and `/patient/profile` are static shells; the others are dynamic |
| Visual review | 1440 and 390 screenshots compared with the reference overview and the sub-view template. Fixed: overview row gaps (now independent columns), open conversation shown first on phones, title truncation at 390 (icon-only Account menu) |
| `npm audit` | not re-run: no dependency changes |
| `mvn test` | not run: backend not modified |

### Brief reconciliation (2026-10-11, second pass)

The formal Phase 4C brief arrived after the first pass. It confirmed the 4B decisions (homepage headline
kept, real provider data, specialty normalisation, the approved/verified distinction, no payments or
video, backend optimisations documented only, super-admin bug tracked). Gaps against the brief were
closed:

- **Specialty normalisation** (`features/find-care/specialty.ts`):
  - Only spelling equivalents are folded together: case, spacing, punctuation and apostrophes,
    "&"/"and", and British/American spellings (paediatric, gynaecology, orthopaedic, haematology,
    anaesthesia, oesophagus).
  - Different specialties are never merged. The homepage category cards group equivalents under the
    most used spelling.
  - A directory filter with a spelling equivalent is sent as the longest substring the spellings
    share (for example "Paediatrics" → `ediatrics`). That is one normal request to the backend's
    case-insensitive substring filter, and backend values are never changed.
  - The URL and filter chip keep the readable name.
- **Appointment details:** a drawer loaded fresh from `GET /appointments/{id}` shows the time with
  its zone, duration, mode, any proposed time, when it was requested and last updated, a reference,
  and a plain-language explanation of the status. It opens from each row's Details button and from
  the overview's "View details" (`?appointment=`). 404 shows "Appointment not found"; another
  patient's id gets the same answer from the API.
- **Text consultation** (`/patient/appointments/consultation`, `ConsultationController`):
  - Available only for CONFIRMED text appointments. Opening notifies the doctor, so it happens only
    when the patient presses "Join text consultation", never on page load.
  - The patient can then read the thread, send messages and refresh. The view is read-only once the
    doctor ends it.
  - In-person and unconfirmed appointments get a clear "not available" explanation.
  - Consultation and appointment notifications on the overview link to the right view.
- **Session expiry:** any patient API call that answers 401 (a load or an action) replaces the whole
  workspace, and all patient data held in memory, with "Your session has ended". The "Sign in
  again" link returns to the same page and query.
- **Permission refusals:** a 403 shows "You do not have permission to see this information" with no
  retry. Outages keep a retry.
- **Browser storage:** no patient data is written to local or session storage or IndexedDB (checked
  with a search of `src/`).
- **Auth regression fix:** `/verify` sent its single-use token twice when the effect re-ran (React
  Strict Mode in development), and the second response replaced the success message with "invalid
  or expired". This was found by the auth live spec against `next dev`; 4A had tested only against a
  production build. The page now submits each token once (`verify-email-panel.tsx`).
- **Booking entry point:** the brief asks for navigation to `/find-care` for new appointments. Inside
  the workspace, "Find a doctor" opens `/patient/find-a-doctor`, which is the same Phase 4B search
  and booking component (`FindCare` with `basePath`). Patients keep the sidebar, and sign-in returns
  there. `/find-care` itself is unchanged. Say if you would rather it link to `/find-care`.

### Patient sidebar: support classification

| Sidebar item | Route | Status | Basis |
|---|---|---|---|
| Overview | `/patient` | Fully functional, except the payments card | appointments, records, lab results, requests, notifications, profile |
| Find a doctor | `/patient/find-a-doctor` | Fully functional | `/providers`, `/availability`, `POST /appointments` |
| Appointments | `/patient/appointments` (+ `/consultation`) | Fully functional, with one gap | list, details, cancel, accept/decline proposed time, text consultation, home-care requests; cannot propose a new time (no provider id in the payload) |
| Messages | `/patient/messages` | Fully functional | `/conversations`; care team found by name (backend gap) |
| Medical information | `/patient/records` | Fully functional | records, released lab results, lab requests, medication reminders, vitals, consent |
| Payments | `/patient/payments` | Presentation-only | payments out of scope; labelled fictional, states that MedNet takes no payments |
| Help & support | `/patient/help` | Partially supported | factual guidance and links to real features; no support team or ticket API, stated plainly |
| Profile (Account menu) | `/patient/profile` | Fully functional | `GET/PUT /patients/me/profile` |

There are no placeholder pages: every route shows real data, real guidance, or labelled example data
with an explanation.

### Validation after the second pass

| Check | Result |
|---|---|
| Resources | 4.1–4.6 GB free before starting. One API (heap capped at 768 MB) and one `next dev` at a time; browser tests limited to 2 workers; all temporary servers stopped afterwards |
| `npm run lint`, `npm run typecheck` | pass |
| `npm test` (Vitest) | **119/119** (12 files); new `specialty.test.ts` (4) |
| `npm run test:e2e` (2 workers) | **266 passed, 1 failed, 15 skipped.** The failure was the new notification link at 820px: a 22px target, below the 40px rule. It was enlarged and the affected layout tests re-run: **42/42** across the three widths. New stubbed tests: details drawer (+ axe), join-on-request consultation, in-person "not available", session expiry during a load and during an action, 403 denied state, another patient's appointment id → not found |
| Live API (real Spring Boot, throwaway H2; not mocked) | patient **33/33** (11 flows × 3 widths, adding details plus a text consultation with the doctor replying, and a session ended elsewhere); find-care **24/24**; auth **27/27** with the super-admin case excluded (known backend defect) |
| `npm run build` | pass (`/patient/appointments/consultation` is dynamic) |
| Screenshots | overview, appointments, details drawer, consultation and records at 390/820/1440, compared with the reference overview and sub-view template. No overflow; the drawer is a bottom sheet on phones |

### Files (4C)

- `web/src/app/patient/layout.tsx`; `page.tsx`, `profile/`, `find-a-doctor/`, `appointments/`, `messages/`,
  `records/`, `payments/` and `help/page.tsx`
- `web/src/features/patient/`: `patient-api.ts`, `format.ts`, `use-resource.ts`, `patient-workspace.tsx`,
  `view-frame.tsx`, `overview.tsx`, `appointments.tsx`, `service-requests.tsx`, `records.tsx`,
  `health-tracking.tsx`, `sharing.tsx`, `care-team.ts`, `messages.tsx`, `profile-form.tsx`,
  `presentation-views.tsx`, `fictional.ts`, `patient.test.tsx`
- Changed: `features/find-care/find-care.tsx`, `availability-drawer.tsx` (+ test),
  `components/healthcare/appointment.tsx`, `records.tsx`, `app/globals.css`
- Removed: `components/patient-dashboard.tsx`, `components/patient-profile.tsx`
- Tests: `web/e2e/patient.spec.ts`, `web/e2e/support/patient-stub.ts`, `web/e2e/patient.live.spec.ts`
- Second pass: `features/patient/consultation.tsx`, `app/patient/appointments/consultation/page.tsx`,
  `features/find-care/specialty.ts` (+ `specialty.test.ts`); changed `provider-api.ts`,
  `features/home/home-directory.tsx`, `features/auth/verify-email-panel.tsx`, and the patient
  `appointments.tsx`, `overview.tsx`, `patient-api.ts`, `patient-workspace.tsx`, `records.tsx`, `use-resource.ts`

### Open points from 4C

- Booking entry point inside the workspace is `/patient/find-a-doctor` (the same 4B component), not `/find-care`; please confirm.
- Please review the overview's added Requests and Notifications cards and the Account menu.
- The care-team name matching should be replaced once the API returns provider ids (above).
- Help & support has no approved content or support channel. The page states this rather than inventing
  contact details.

## Phase 4B log — public website

### Completed

- **Homepage** rebuilt to the reference layout, measured at 1440px:
  - Hero: 105° brand gradient; 72px semibold display at 1440, scaled down at 820 and 390; arched photo
    bleeding off the right edge; floating card.
  - Category cards, provider cards, the dark four-point band, the three steps on the warm surface, and
    the shared header and footer.
  - The hero search is a plain GET form to `/find-care`, so it works before JavaScript loads.
- **Live data instead of fictional data:**
  - Category cards are the specialties approved providers actually list, with counts, plus a
    "Laboratory requests" card that links to the Services page.
  - Provider cards are real approved providers with their real next open time.
  - Loading, empty and error states are built.
- **Content changed because the reference describes features the backend doesn't have** (details in
  `DESIGN_SYSTEM.md` §14):
  - Removed: fees ("USD 35"), "Transparent fixed fees", "Provider payout tracking", payment
    authorisation, "Video consultation", and the Location and Availability search fields.
  - "Verified" reads "Approved", because credential verification is unresolved (OQ-09).
  - The four-point band and the three steps describe implemented capabilities only.
- **New `/find-care` (doctor discovery and appointment requests)** over `GET /providers`,
  `GET /providers/{id}/availability` and `POST /appointments`:
  - Search by name or specialty, with a specialty filter, removable filter chips and pagination. State
    lives in the URL.
  - An open-times drawer lists slots grouped by day in Liberia time (GMT, labelled).
  - Signed-out visitors go to `/sign-in?redirect=…` and return with the provider and time selected.
  - Patients can send the request.
  - Provider and staff accounts are told that only patients can request.
  - A 409 for an incomplete profile links to `/patient/profile`. A 409 for a taken time refreshes the
    list.
  - The reference has no discovery screen, so the layout is an interpretation (§14).
- **About** ("How it works") and **Services** have no reference layout. They are rebuilt with
  `PageIntro` and Phase 3 cards. Every statement is checked against the README's current scope and the
  OQ-07 consent policy. Services has a plain "Not available on MedNet" list: emergency care (the
  approved line), payments, video and voice, and electronic prescribing.
- **Navigation:**
  - "Find care", "Book an appointment" and the footer's "Find a doctor" now go to `/find-care`.
  - "Laboratory services" goes to `/services#laboratory`.
  - "For providers", "Help", "Contact", Privacy, Accessibility and Terms stay hidden, because there is
    no page or approved policy content. A browser test asserts that no internal link is dead.
- **Shared component changes:**
  - `DoctorCard` reads "Approved provider" instead of "Profile verified", has a configurable action
    label and a selected state, and wraps long names.
  - The auth pages' trust marker now reads "Approved providers".
  - New `PageIntro` (`components/layout/page-intro.tsx`).
- **Legacy CSS:** 185 unused legacy rules (old homepage, about/services and auth styles) were removed from
  `globals.css`, which went from 589 to 383 lines. Only rules whose every selector used a class found
  nowhere in the source were removed.

### Integration testing against a running backend

The setup was the same as 4A: the real API on port 8081 with a throwaway H2 database, and the production
build behind the same-origin proxy. Data was created through the real endpoints only:

- The admin creates and verifies accounts, then creates and approves the provider records.
- Each provider publishes availability from their own session.
- The admin session cookie is reused, to work around the 4A admin sign-in defect.

The helper is `e2e/support/seed-directory.ts`.

`e2e/find-care.live.spec.ts`: **24 passed** at 390, 820 and 1440px.

| Flow | Result |
|---|---|
| Homepage | provider cards are exactly the API's first three with real next times; specialty links go to filtered results; no "USD", "Video consultation", "payout" or "verified" text; no horizontal scroll; axe 0 violations |
| Hero search | specialty search → `/find-care?search=…` → the two seeded providers, one with "Tomorrow, …", one with "No open times" |
| Filters | specialty chip; no-match empty state; "Clear filters" |
| Signed-out request | choose time → sign in → back on `/find-care?provider=…&slot=…` with the time still selected |
| Booking | API's 409 "Complete your patient profile" → profile link; after completing the profile, "Request sent"; `GET /appointments` shows `PENDING`, `TEXT` |
| Taken time | another patient takes the slot first → 409 → "That time is no longer available" and the list refreshes (2 → 1 slots) |
| Provider account | told that requests come from patient accounts; no send button |

### Validation (final run)

| Check | Result |
|---|---|
| `npm run lint`, `npm run typecheck` | pass |
| `npm test` (Vitest) | **107/107** (10 files). New `find-care.test.tsx` (12): Liberia-time formatting (today/tomorrow, Monrovia day boundary), day grouping, consultation-mode labels, specialty counting, URL building, and every drawer outcome (signed-out redirect, patient send with CSRF, 409 profile, 409 taken + refresh, provider account, empty, not listed) |
| `npm run test:e2e` (390/820/1440) | **159 passed, 15 skipped** (Phase 3 skips). New `e2e/public.spec.ts` (27): `/`, `/find-care`, `/about`, `/services` fit with one H1 and axe WCAG A/AA **0 violations**; no dead internal links; real specialties only and no fees; API-down error states; drawer keyboard use, Escape and URL reset; GET hero search |
| Live API | find-care **24 passed**; auth (4A) spec still passes |
| `npm run build` | pass; `/find-care` is dynamic |
| Visual review | full-page 1440/820/390 screenshots compared with the reference captures (hero, cards, band, steps, footer). Grid defect found and fixed: the fixed-width photo had squeezed the text column to 693px, and it now matches the reference's 909px search bar and right-edge bleed. Category chevrons and the white section surface fixed |
| `npm audit` | not re-run: no dependency changes |
| `mvn test` | not run: backend not modified |

### Files (4B)

- `web/src/app/(public)/page.tsx`, `about/page.tsx`, `services/page.tsx`, `find-care/page.tsx` (new)
- `web/src/features/home/` — `home-hero.tsx`, `home-directory.tsx`, `home-sections.tsx`
- `web/src/features/find-care/` — `provider-api.ts`, `format.ts`, `find-care.tsx`, `availability-drawer.tsx`,
  `find-care.test.tsx`
- `web/src/components/layout/page-intro.tsx` (new), `public-nav.ts`, `public-header.tsx`;
  `components/healthcare/doctor-card.tsx` (+ test); `features/auth/auth-shell.tsx` (wording);
  `app/(dev)/foundation/page.tsx` (prop rename); `app/globals.css` (legacy pruning)
- Tests: `web/e2e/public.spec.ts`, `web/e2e/find-care.live.spec.ts`, `web/e2e/support/seed-directory.ts`

### Open points from 4B

- **Hero headline:** "Trusted care, wherever you are." is the reference's own line. It is kept as the
  approved brand headline, but please confirm it is an acceptable claim.
- **Specialty is free text** in provider applications, so "Pediatrics" and "Paediatrics" count as
  separate categories. A controlled specialty list would be a backend and policy decision.
- **No public provider profile endpoint** (`GET /providers/{id}`). A provider linked directly (from the
  homepage or a sign-in return) who is not on the current results page is found by loading up to 100
  directory entries.
- **Booking response time:** each results page makes one availability request per provider (up to 12)
  to show the next open time. That's acceptable at this scale; a `nextAvailableAt` field in the directory
  would remove it.
- **Pages not built**, because there is no reference layout or approved content: For providers, Help,
  Contact, Privacy, Accessibility, Terms.

## Phase 4A log — authentication and account access

### Backend defect found (not fixed: backend changes not authorised)

**The configured super admin (`MEDNET_ADMIN_EMAIL`/`MEDNET_ADMIN_PASSWORD`) can sign in only once per
API process.** Verified live with the correct password: first `POST /api/v1/auth/login` → 204, every
later attempt → 401 "Invalid email or password" until the API restarts.

- Cause: `SecurityConfig.userDetailsService` builds the administrator `UserDetails` once and returns the
  same instance on every lookup. After a successful login, Spring Security's `ProviderManager` erases
  credentials (the default), which nulls the password on that shared `User`.
- Suggested fix: keep the encoded password and build a new `User` per lookup, plus a backend test that
  signs the configured admin in twice.
- Database accounts (patients, providers, staff) and Google sign-in are not affected.

### Completed

- **Pages:** `/sign-in` (sign in, create account, forgot password, resend verification, Google), `/verify`,
  `/reset` (email → code → new password) and `/account` rebuilt on `AuthShell` and Phase 3 components
  (`features/auth/*`). Layout interpretation: `DESIGN_SYSTEM.md` §13. There is no reference design.
- **API:** all calls go through `lib/api` (`features/auth/auth-api.ts`). The duplicated `fetch`/CSRF code
  in `google-signin.tsx` and the old reset/verify pages is gone, along with `password-input.tsx`.
  `apiRequest` gained a `form` option for Spring's form login, and error parsing accepts the auth
  endpoints' plain `{ message }` bodies.
- **Behaviour preserved, checked against the committed originals and the controllers:**
  - The same endpoints, payloads and server messages are used.
  - A 401 on sign-in offers "Resend verification email", and a 503 says sign-in is temporarily
    unavailable.
  - Existing sessions are redirected away from `/sign-in`. The `?redirect=` handling is unchanged: it
    accepts same-origin paths only and rejects `//host`, backslashes and `/sign-in`.
  - `/account` still forwards `ADMIN`/`SUPER_ADMIN` to `/admin`.
  - The reset email link `/reset?email=` still starts at the code step, and the email is removed from
    the address bar after the code is verified.
- **Redirects (unchanged):** `ADMIN`/`SUPER_ADMIN` → `/admin`, `PATIENT` → `/patient`, everyone else
  (including `PROVIDER`) → `/account`. This matches the backend's Google success handler. The provider
  destination is reviewed in 4D; changing it needs the frontend and `SecurityConfig` changed together.
- **New client-side validation** mirrors the backend limits (`DESIGN_SYSTEM.md` §13.4). It focuses the
  first invalid field and does not call the API.
- **Deliberate small changes:**
  - `/account` reports "The MedNet API is unavailable" when the session check fails with a server error.
    It used to show "signed out"; a 401 still shows signed out.
  - Sign-out now reports a failed logout response. It used to ignore the status.
  - The forgot-password button reads "Send recovery code", because the backend emails a code.

### Integration testing against a running backend

The real Spring Boot API ran locally (`mvn spring-boot:run`, Java 21) with no mocks:

- **Database:** a throwaway H2 file database in PostgreSQL mode, the same mode the backend suite uses.
  The local PostgreSQL could not be used because the `.env` credentials for user `mednet` are rejected
  (`28P01`). `.env` and the database were not changed.
- **Origin:** the production web build (`next start`) ran behind a local same-origin proxy that sends
  `/api/*` to the API, as LPAD/nginx does.
- **Accounts:** a local-only super admin was set through environment variables, and test accounts used
  `@mednet.test` addresses.
- **Email:** delivery was off, as in `.env`. `e2e/support/live-backend.ts` stood in for the inbox by
  storing a known token hash in the throwaway database. `/auth/verify` and `/auth/reset-password/*`
  did all of the checking. Repeatable steps are in `web/e2e/README.md`.

`e2e/auth.live.spec.ts` result: **28 passed, 2 skipped** (the admin case runs once per API start, at
1440px, because of the defect above), at 390, 820 and 1440px. Verified flows:

| Flow | Result |
|---|---|
| Anonymous `/account` | signed-out state, link to `/sign-in` |
| Google status | API reports disabled; page shows "not configured" (enabled path covered by stubbed tests) |
| Register | client validation, then the API's 503 "Verification email could not be sent" shown (email off); account created unverified |
| Sign in before verification | 401 → "Sign-in failed…" + "Resend verification email" mode with the email kept |
| `/verify` incomplete / forged token | "incomplete" without an API call / API's "invalid or expired" |
| `/verify` with a valid token | "Email verified", continue to sign in |
| Patient sign-in | wrong password rejected; `?redirect=/patient/profile` honoured; session is `PATIENT`; `?redirect=//evil.example` ignored → `/patient`; `/sign-in` while signed in → `/patient` |
| `/account` + sign out | email and "Patient" only; sign-out ends the session (`/auth/session` → 401) |
| Password reset | generic 202; wrong code → API's 400 message; right code → new password; old password rejected, new one accepted |
| Super admin | sign-in → `/admin`; `/account` → `/admin` |

**Not verified live:**

- Real email delivery: no email provider key is configured.
- The Google OAuth round trip: Google is disabled locally. The UI is covered by stubbed tests, and the
  redirect contract by reading `SecurityConfig`.
- The 503 "temporarily unavailable" sign-in path: covered by stubbed tests.
- PostgreSQL itself, because of the credentials problem above. No migrations changed.

### Validation (final run)

| Check | Result |
|---|---|
| `npm run lint` | pass, 0 findings |
| `npm run typecheck` | pass |
| `npm test` (Vitest) | **95/95 passed** (9 files). New: `AccountPanel` (admin forward, details + CSRF sign-out, failed sign-out, API unavailable), `apiRequest` form encoding and plain-message errors |
| `npm run test:e2e` (Playwright, Edge, 390/820/1440px) | **132 passed, 15 skipped** (Phase 3 skips), including new `e2e/auth.spec.ts` (30): all five auth views fit without horizontal scroll, targets ≥40px, axe WCAG 2.0–2.2 A/AA **0 violations** in every sign-in mode, the Google error and the reset code step; loading state; skip link; keyboard mode switching; validation focus; visible focus ring; digits-only code input |
| Live API (`MEDNET_LIVE_API=1`) | **28 passed, 2 skipped** (above) |
| `npm run build` + `next start` | pass; `/`, `/about`, `/services`, `/sign-in`, `/verify`, `/reset`, `/account`, `/patient`, `/admin` → 200; `/foundation`, `/foundation/workspace/admin` → 404 |
| Visual review | Screenshots at 390/820/1440 of sign-in, register, validation errors, reset code step, verify success and signed-in account: no clipping or overflow; type weights follow §12.3 (4) |
| `npm audit` | not re-run: no dependency changes in 4A |
| `mvn test` | not run: backend not modified |

### Files (4A)

- `web/src/app/(auth)/{sign-in,verify,reset,account}/page.tsx` — server pages using `AuthShell`
- `web/src/features/auth/` — `auth-api.ts`, `destination.ts`, `auth-shell.tsx`, `sign-in-panel.tsx`,
  `verify-email-panel.tsx`, `reset-password-panel.tsx`, `account-panel.tsx`, `auth-panels.test.tsx`,
  `destination.test.ts`
- `web/src/lib/api/{client,errors}.ts` (+ `client.test.ts`); `features/workspace/workspaces.ts`
  (`homePathForRole`)
- Removed: `web/src/components/google-signin.tsx`, `web/src/components/password-input.tsx`
- Tests and tooling: `web/e2e/auth.spec.ts`, `web/e2e/auth.live.spec.ts`,
  `web/e2e/support/live-backend.ts`, `web/e2e/README.md`. In `web/playwright.config.ts`,
  `PLAYWRIGHT_BASE_URL` targets an already-running server and `*.live.spec.ts` runs only when
  `MEDNET_LIVE_API=1`.

### Environment notes

- Port 8080 is held by Apache `httpd`, so `next dev`'s default proxy target is not MedNet on this
  machine. Run the API with `SERVER_PORT=8081` and set `MEDNET_API_URL=http://localhost:8081`.
- Next 16 allows one `next dev` per project. A dev server left on port 3000 had a crashed render
  worker (500 on `/foundation/workspace/*`). It was restarted with the same command
  (`next dev --port 3000`).

## Phase 3 log

### Completed

- **Measurement:** reference component styles were measured in a headless browser at 1440px (buttons,
  fields, badges, alerts, cards, tables, timeline, avatar). Values are in `DESIGN_SYSTEM.md` §12.1.
- **Components:** 50+ components across `components/ui` (Groups A, B and D), `components/healthcare`
  (Group C) and `components/workspace`. The inventory is in `DESIGN_SYSTEM.md` §12.2.
- **Status badges** use the backend's actual database status values only (appointments, lab results,
  service requests).
- **Mobile button clipping** from the reference is fixed: labels wrap and minimum heights are kept.
- **Showcase:** `/foundation` covers tokens, typography, buttons, forms, badges and alerts, cards and
  metrics, navigation, tables and filters, overlays, states, healthcare components and workspace shells.
  Each section names its reference location. It is development-only and returns 404 in production.
- **Accessibility review:** an independent review found 3 high, 8 medium and several low-severity
  issues. All high and medium issues were fixed, plus most low ones. The rest are documented below.
- **Tokens added:** `action-border`, `critical-border`, `text-title`, `text-button`, `text-badge`.
  `control-border` was darkened for contrast.
- **Dev dependency added:** `@axe-core/playwright`, for automated WCAG scans in browser tests.
- **Deduplication:** `WorkspaceShell` now uses the shared `Avatar`.

### Validation (final run)

| Check | Result |
|---|---|
| `npm run lint` | pass, 0 findings |
| `npm run typecheck` | pass |
| `npm test` (Vitest) | **63/63 passed** (7 files): variants, target sizes, loading and disabled behaviour, field labelling and errors, keyboard toggling, live-region policy, tabs/menu/pagination keyboard, tooltip, table states, backend status vocabularies, healthcare components |
| `npm run test:e2e` (Playwright, Edge, 390/820/1440px) | **102 passed, 15 skipped** (fidelity runs at 1440px only; rail-label test below 1200px only) |
| — layout | no horizontal scroll or clipped controls at any width; 44px buttons and fields, 40px professional controls; tables stack below 768px |
| — behaviour | form validation; visible 3px focus ring; dialog focus trap, Escape and return focus; confirm starts on Cancel; drawer; menu keyboard; toast announce, dismiss and focus return; filters update a live count; drag-to-backdrop does not close |
| — fidelity | 7 checks of computed styles against the measured reference values (deviations asserted as the approved tokens) |
| — accessibility | axe-core WCAG 2.0/2.1/2.2 A and AA scan of the full showcase: **0 violations** at all three widths |
| `npm run build` | pass |
| Production `next start` | `/`, `/about`, `/services`, `/sign-in`, `/patient`, `/admin` → 200; `/foundation`, `/foundation/workspace/admin` → **404** |
| `npm audit` | 0 production vulnerabilities; 5 high in dev-only `braces` via `@next/eslint-plugin-next` (pre-existing, unchanged) |
| `mvn test` | not run, because the backend was not modified |

### Visual fidelity

- The computed styles match the measured reference values, apart from the approved token
  normalisations and the documented deviations.
- Screenshots of the showcase were compared by eye with the reference's Components, Patterns, patient
  dashboard and operations views at 1440px and 390px.
- A pixel-diff against the reference was not practical: the reference is a prototype page with
  different content arrangement, so component-level computed-style checks were used instead.

### Unresolved visual discrepancies (documented, not fixed)

- Exact icon glyphs are Lucide equivalents. The reference draws its own inline SVGs.
- The reference's 10–11px table, helper and error text is raised to 11–13px.
- Card and section titles follow the rendered 400 weight rather than the "Semibold" label on the
  reference's type-scale panel. Confirmed 2026-10-10 with the Phase 4 authorisation (follow the rendered reference).

### Accessibility findings still open (low severity)

- `DataTable` renders cells twice (table plus stacked list, one hidden), so cell renderers must be
  stateless.
- `MetricCard` tone is shown only by its edge colour. The label and hint text must carry the meaning,
  which is documented as a usage rule.
- The disabled 42% opacity (from the reference) gives 1.9–2.6:1 contrast. That is exempt under WCAG
  but hard to read.
- `cn()` doesn't merge conflicting classes.

## Phase 2 log

### Baseline (before any change)

| Check | Result |
|---|---|
| `npm ci` | OK |
| `npm run lint` | pass, 0 findings |
| `npm run build` | pass, 11 routes |
| `npm audit --omit=dev` | 0 vulnerabilities (5 high in dev-only dependencies, pre-existing) |

### Completed

- **Tokens:** every reference token is in Tailwind 4 `@theme` (`web/src/app/globals.css`). Default palette,
  radii, shadows and breakpoints are cleared. There are semantic aliases, a type scale, 768/1200/1440
  breakpoints, a focus-visible rule and reduced-motion support.
- **Typography:** Source Sans 3 via `next/font`, replacing DM Sans and DM Serif Display.
- **Legacy styles:** layered under the token utilities, and their variables re-pointed at approved
  tokens. Existing pages now use the approved palette and font while keeping their layouts.
- **Layouts:** a `(public)` route group with shared `PublicHeader` and `PublicFooter`, replacing the
  per-page `SiteHeader` and `SiteFooter`. An `(auth)` group. `WorkspaceShell` for all workspace styles
  (icon and numbered navigation). No URL changed.
- **Workspace registry:** all 9 designed workspaces with navigation, routes, backend role (or `null`) and
  per-view backend support.
- **API layer:** `lib/api` with CSRF, session cookie, typed `ApiRequestError` parsed from the backend
  error body, `getSession()` and `signOut()`.
- **Presentation-only convention:** `lib/fictional.ts` plus `FictionalDataBadge`.
- **Preview:** a development-only `/foundation` page showing tokens and every workspace shell.
- **Hero image:** self-hosted at `public/images/hero-care.jpg`; licensing notes are in `DESIGN_SYSTEM.md` §5.
- **Tooling:** Vitest, Testing Library and Playwright, plus `typecheck`, `test`, `test:watch` and
  `test:e2e` scripts.

### Validation (final run)

| Check | Result |
|---|---|
| `npm run lint` | pass, 0 findings |
| `npm run typecheck` | pass |
| `npm test` (Vitest) | 4 files, **23/23 passed** |
| `npm run test:e2e` (Playwright, Edge, 390/820/1440px) | **48/48 passed**: public pages render with the shared header and footer and no horizontal scroll; Source Sans 3 applied; nav reachable at every width; patient, doctor, admin and collection-agent shells fit the viewport with a full-height sidebar; `/sign-in`, `/verify`, `/reset`, `/patient`, `/admin`, `/account` still respond and render |
| `npm run build` | pass, all original routes plus `/foundation` |
| Production `next start` | `/`, `/about`, `/services`, `/sign-in` → 200; `/foundation`, `/foundation/workspace/patient` → **404** |
| Visual review (screenshots) | Shells at 1440 and 390px, homepage at 1440px, mobile menu at 390px reviewed. Rail wordmark overlap and header/content misalignment found and fixed |
| `mvn test` | not run, because the backend was not modified |

Limits:

- Authenticated flows were not exercised end-to-end. The API was not running, so `/patient`, `/admin`
  and `/account` were checked only for rendering, not sign-in.
- Playwright runs against `next dev`, because the preview routes are development-only.

### Deviations and interpretations

See `DESIGN_SYSTEM.md` §5 (icons) and §10 (layouts). In summary:

- The public mobile menu toggle is an interpretation.
- Header and footer links without pages are hidden.
- "Book an appointment" goes to `/services` for now.
- The workspace rail below 1200px is a responsive adjustment.
- The white sidebar wordmark is a contrast fix.
- The workspace-switch select is not built.
- The focus outline uses solid action green, because the stated focus colour is too faint.
- Two patient nav icons differ from the reference's repeated heart.

## Outstanding issues and dependencies

**Carried forward from Phase 2:**

- Brand green is provisionally retained as in the reference (reconfirmed 2026-10-10; final approval pending). The `#85928C` control border is approved.
- The hero image needs its licence and provenance verified before production.
- ~~Authentication flows have not yet been tested against a running backend.~~ Done in 4A, except
  real email delivery, the Google round trip and PostgreSQL (see the Phase 4A log).
- The dev-dependency audit findings are documented above.
- Interpretations are logged in `DESIGN_SYSTEM.md` §§10–13.

**New in Phase 3:**

- Date and time selectors, document upload and the specimen receipt checklist were not built. They
  depend on availability data, an upload API decision and a laboratory API respectively.
- Routing doctors (`PROVIDER`) to `/provider` after sign-in changes current behaviour and needs
  approval in Phase 4D. The backend's Google success handler would need the same change.

**Tracked issue log (confirmed 2026-10-11):**

| Issue | Status |
|---|---|
| Super admin can sign in only once per API process (shared `UserDetails` password erased) | **resolved in 4E** (fresh principal per lookup; regression tests) |
| Local PostgreSQL credentials in `.env` rejected | open; live tests use a throwaway H2 database instead; no secrets recorded here |
| Hero image licence and photographer credit | open |
| Brand green final confirmation | open (provisionally retained) |
| Dev-dependency audit findings (5 high, `braces` via `@next/eslint-plugin-next`) | open, dev-only |
| Backend directory performance: `nextAvailableAt` on `GET /providers` (avoids one availability call per card); `GET /providers/{id}` (avoids loading 100 entries for a linked provider); a controlled specialty list | proposed, not implemented |
| `AppointmentDetails` lacks `providerId`, `specialty` and `rescheduleRequestedBy` | **resolved in 4D** (additive backend change) |
| Provider-wide pending lab results endpoint (Clinical work makes one call per patient) | proposed (Phase 4D log) |
| Google sign-in success handler sends providers to `/account` (forwarded to `/provider` by the frontend) | **resolved in 4E** (`PROVIDER → /provider`) |
| `ADMIN` can suspend other `ADMIN`/`SUPER_ADMIN` accounts (no role check on account status) | **resolved in 4F** (403; negative tests) |
| `AdminRequestSummary` lacks the assigned staff account | **resolved in 4F** (`assignedStaffPublicId`) |
| Laboratory cannot read back its submitted results or their review status | **resolved in 4G** (`GET /partner/lab-results`) |
| Google sign-in sends `LABORATORY` to `/account` (forwarded to `/laboratory`) | **resolved in 4G** (unit-tested; Google flow not run locally) |
| Home-care partners have no workspace (`HOME_CARE` lands on `/account`) | open (Phase 4G review) |
| Real Google OAuth sign-in never run end-to-end | open; needs a configured OAuth client |
| PostgreSQL integration profile not run in Phases 4D–4G (H2 used) | open; run before deployment |
| Patient-initiated rescheduling | approved in principle; separate patient-workspace enhancement |
| Incomplete Figma reference pages: sign-in/account, discovery, About, Services, For providers, Help, Contact, Privacy, Accessibility, Terms; patient sub-views are a generic template | open; interpreted pages documented in `DESIGN_SYSTEM.md` §13–15 |

**New in Phase 4A:**

- **Backend defect:** the configured super admin can sign in only once per API process (Phase 4A log).
  The fix needs backend authorisation.
- **Local PostgreSQL credentials:** the `.env` datasource password for `mednet` is rejected by the local
  server, so the API cannot start against PostgreSQL until it is corrected.
- **Email delivery is not configured locally** (`EKDSEND_API_KEY` empty), so registration returns 503
  by design. Configure a test key to exercise real verification and reset emails.

**Backend capabilities missing for designed screens.** These will be presentation-only until approved:

- Payments, receipts, payouts, provider earnings, the financial officer workspace (out of scope per
  `AGENTS.md`).
- Collection provider and collection agent: no role, pickup, custody or offline-sync API.
- Customer support: no role or ticket API.
- Laboratory: no specimen receipt, test catalogue, pricing, result validation and release states, or
  audit history endpoints. Existing: `/partner/service-requests` (list, schedule, status) and
  `POST …/lab-result`.
- Admin: no task queue, escalation or report endpoints. Security and configuration are only partly
  covered by audit.

**Unresolved policy** (gated per `AGENTS.md`): result-review deadlines, critical-value escalation,
emergency routing, recollection fees, payment timing.

**Assets:** confirm the hero photo's licence and photographer credit. Brand green awaits owner
confirmation.

**Technical:**

- `admin-portal.tsx` still uses direct `fetch`; it migrates in 4E. (Patient pages migrated in 4C.)
- Remaining legacy CSS in `globals.css` serves only `admin-portal.tsx`; it goes with 4E.
- `admin-portal.tsx` (1,594 lines) needs splitting during Phase 4E.
- Dev-dependency audit findings to review.
