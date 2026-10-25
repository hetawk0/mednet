# MedNet security audit — Phase 5B and 5B closure (2026-10-11), Phase 5E regression check (2026-10-12)

Historical report: the authentication design has since changed to MedNet-managed account passwords
and sessions, with verification and recovery email sent through EKDSend. Google/OAuth findings below
describe the prior implementation and are not current setup instructions. PostgreSQL and deployment
security findings still require owner review.

Scope: the Spring Boot API, the Next.js web client, configuration and dependencies. All Phase 5B work
ran **without Supabase**: the database password exposed in a chat on 2026-10-11 has not been rotated
(the old password was still accepted that day), and no connection has been made with it since. Every
result below names the database or mode it used. This file contains no credentials, tokens or
patient data.

## Status at a glance

| Area | Status |
|---|---|
| Rate limiting on authentication and email endpoints | ✅ implemented and tested (H2, in-process) |
| Session controls (sign-out, idle and absolute lifetime, sign-out on account changes) | ✅ implemented and tested (H2) |
| Account-takeover fixes V1, V2 | ✅ fixed, regression tests retained |
| Dependency advisories | ✅ backend 0, web production 0; one development-only advisory deferred |
| PostgreSQL re-verification | ⛔ **blocked** — credential rotation not confirmed |
| Google OAuth end to end | ⛔ **blocked** — no development OAuth client configured |

## Launch-blocking issues

| # | Issue | Status |
|---|---|---|
| 1 | Supabase database password exposed and **not rotated** | Owner action required; blocks every PostgreSQL check |
| 2 | Google sign-in never run end to end | Needs a development OAuth client; keep `GOOGLE_OAUTH_ENABLED=false` in production until it passes |
| 3 | Ownership of the existing accounts in the Supabase `postgres` database unknown | Owner confirmation required; nothing may write there |
| 4 | Rate-limit counters are per API process | Fine for one instance; **more than one instance needs shared state** (see "Rate limiting") |
| 5 | `MEDNET_TRUSTED_PROXIES` must name the production reverse proxy | Without it every visitor shares the proxy's allowance (see "Client addresses") |

The earlier blocker "no rate limiting" is closed by this phase.

## Security-control matrix

Verified controls first; recommendations and blocked items follow in their own tables.

### Verified controls

| Control | Current implementation | Verification | Result | Remaining risk | Production readiness |
|---|---|---|---|---|---|
| Deny-by-default API access | `SecurityConfig`: only health, auth entry points, `GET /providers`, `GET /providers/*/availability` public; admin paths need `ADMIN`/`SUPER_ADMIN`; all else signed in, plus role/ownership checks in controllers and services | `signedOutCallersGetUnauthorizedFromEveryAccountEndpoint` (31 endpoints) | pass (H2) | — | Ready |
| Sign-up cannot take over an unverified account (V1) | Password set only on new accounts | `signUpCannotReplaceThePasswordOfAnExistingUnverifiedAccount` | pass (H2) | — | Ready |
| Google link drops an unconfirmed password (V2) | `GoogleOAuth2UserService` | `googleSignInDropsAPasswordThatWasNeverConfirmedByTheMailboxOwner`, `…KeepsThePasswordOfAVerifiedAccount` | pass (unit) | Live flow not run | Ready in code; live test blocked |
| Sign-in failure limits | 10 failures per account, 50 per client, per 15 min; refused before the password check | `repeatedFailedSignInsAreLimitedPerAccountUntilTheWindowPasses`, `guessingOneAccountFromManyAddressesIsStillLimited`, `oneAddressIsLimitedAcrossAccountsWhileOtherAddressesAreNot` | pass (H2) | An attacker can hold a known account in a 15-minute cool-down | Ready for one instance |
| Email-sending limits (sign-up, resend, forgot-password) | 5 per address, 20 per client, per hour, shared across the three | `verificationEmailsAreLimitedAcrossSignUpAndResend`, `passwordResetRequestsAreLimitedAlikeForUnknownAndKnownAddresses` | pass (H2) | — | Ready for one instance |
| Reset-code guessing | 10 code/ticket checks per address per hour (any number of clients) plus 30 per client per 15 min; each code still dies after 5 wrong tries and 10 minutes | `resetCodeGuessingIsLimitedPerAccountAcrossAddresses` | pass (H2) | ≈ 240 guesses/day/account → ≈ 0.02 % daily success | Ready for one instance |
| Verification-link checks | 30 per client per 15 min (tokens are 256-bit) | `verificationLinkChecksAreLimitedPerClient` | pass (H2) | — | Ready |
| Forged forwarding headers | Client = TCP peer; `X-Forwarded-For` read only from `MEDNET_TRUSTED_PROXIES`, right to left | `forwardingHeadersFromUntrustedClientsCannotEscapeTheLimit`, `aTrustedProxyForwardsEachClientsOwnAllowance`, `forwardingHeadersCountOnlyFromTrustedProxies` | pass | Needs correct proxy list in production | Ready once configured |
| Non-enumerating responses | Same 202 message for known/unknown addresses; limits count before the account lookup; one generic `429` message | abuse tests above | pass (H2) | — | Ready |
| Bounded limiter memory | LRU map, `maxTrackedKeys` 50 000; account keys are truncated SHA-256 of the address | `trackedCountersStayWithinTheConfiguredBound`, `accountKeysAreNormalizedAndDoNotContainTheAddress` | pass (unit) | Flooding with many addresses can evict counters early | Ready |
| Sign-out | `POST /auth/logout` with CSRF invalidates the session | `signOutEndsTheSession` | pass (H2) | — | Ready |
| Absolute session lifetime | 12 h from sign-in (`SESSION_ABSOLUTE_TIMEOUT`), idle 30 min (`SESSION_IDLE_TIMEOUT`) | `sessionsEndAfterTheAbsoluteLifetimeEvenWhenActive` | pass (H2) | — | Ready |
| Sessions end on account changes | Password reset; admin password, email or role change; suspension; verification withdrawn; deletion | `completingAPasswordResetSignsOutExistingSessions`, `suspensionAndRoleChangesSignOutExistingSessions` (both fail with the fix removed) | pass (H2) | Per-process registry (as for limits) | Ready for one instance |
| Real-API browser flows with all of the above | auth, admin, find-care, patient, provider, laboratory live specs | Playwright against an H2 API | 174/174 (H2), no limit tripped | Not yet on PostgreSQL | Pending PostgreSQL re-run |
| Backend dependencies | Spring Boot 4.1.1 + Tomcat 11.0.26, Jackson 3.1.7/2.21.7 overrides | OSV scan, 154 artifacts | 0 advisories | — | Ready |
| Web production dependencies | — | `npm audit --omit=dev` | 0 | — | Ready |
| Web security headers | `frame-ancestors 'none'`, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, no `X-Powered-By` | `next start` inspection | pass | No script CSP yet | Ready (baseline) |
| Home-care partner access (5C) | `HOME_CARE` only, active and verified; own assignments only (others `404`); laboratory, patient and clinical endpoints `403`; no patient identity in responses; schedule once and in the future; staged transitions; stale actions `409` | `MedNetApplicationTests.homeCareRequestsStayWithTheAssignedStaffMember`, `home-care.spec.ts` (mocked), `home-care.live.spec.ts` (real API) | pass (H2, mocked); real API 24/24 (H2) | Not yet on PostgreSQL | Ready; PostgreSQL re-run pending |
| Phase 5D endpoints | Review queue: PROVIDER only, own care patients, counts only for granted consent, no result text; paged admin lists keep `ADMIN`/`SUPER_ADMIN` (path rule) and the admin service-request view still omits care details; paged lab and patient results keep the laboratory's own assignments and released-only rules; public `GET /providers/{id}` and `/providers/specialties` expose approved directory data only; `/providers/me/application` stays signed-in | `directoryEntriesCarry…`, `administratorListsCanBePaged…`, `theReviewQueueListsOnlyThisProvidersCarePatientsAndNoResultText`, `laboratoryAndPatientResultListsCanBePaged…` (incl. revoked consent, other doctor, other laboratory, patient/lab/admin `403`, signed-out `401`) | pass (H2) | Not yet on PostgreSQL | Ready; PostgreSQL re-run pending |
| Preview workspaces | 404 in production unless `MEDNET_PREVIEW_WORKSPACES=enabled`; fictional data; no API calls | `preview.spec.ts` on a production build (54) | pass | — | Ready |
| CSRF, cookies, CORS, caching, errors, logging | See "Verified controls (detail)" | Code review, existing and new tests | pass | — | Ready |

### Recommendations (not implemented)

| Recommendation | Why | Notes |
|---|---|---|
| Shared rate-limit and session state (Redis or a database table, or limits at the edge proxy) before running more than one API instance | In-memory counters and the session registry are per process; N instances multiply each allowance by N and miss sign-outs on other instances | Not needed for a single instance |
| Full Content-Security-Policy with nonces | Blocks injected scripts | Needs dynamic rendering for every page (Next proxy); performance and layout testing task |
| Idle-timeout warning in the web client | Users lose unsaved input at 30 minutes idle | Product decision |
| Alert on repeated `Authentication rate limit reached` log lines | Early signal of credential stuffing | Log lines carry bucket and scope only, never addresses |

### Blocked tests

| Test | Blocked by |
|---|---|
| Manual `mvn -Ppostgres-it verify` against the existing database | Credential rotation; automated isolated PostgreSQL CI is configured below |
| Seven live-API suites (incl. `home-care.live`) against `mednet_e2e_test` | Credential rotation |
| `AuthenticationAbuseTests` against PostgreSQL | Credential rotation (needs a PostgreSQL variant; the limiter itself holds no database state) |
| Google OAuth end to end, per role | Development OAuth client |

## PostgreSQL CI verification

`.github/workflows/verify.yml` is configured to run the PostgreSQL integration/workflow profile against a fresh,
ephemeral PostgreSQL service. The workflow creates a randomly credentialed, non-superuser role that
owns a database ending in `_test`; the test harness refuses to reset any database that does not meet
those ownership/name checks. These CI credentials are generated per run and are not reused in any
deployment. A successful CI run verifies migrations and application workflows against PostgreSQL,
but does not confirm production database ownership, credential rotation, or the safety of existing
production data. Those owner checks remain blocked until the deployment owner confirms them.

## Rate limiting

Application-level, in `com.mednet.auth.security`: `AuthRateLimiter` (fixed windows starting at the
first counted request, in memory), `ClientAddressResolver`, `SignInRateLimitFilter`; settings
`mednet.rate-limit.*` (`AuthRateLimitProperties`). No new infrastructure.

| Operation | Per client address | Per account (email) | Counts |
|---|---|---|---|
| Sign-in `POST /auth/login` | 50 failures / 15 min | 10 failures / 15 min | Failed sign-ins only; a success clears the account's count. When limited, even a correct password gets `429`. |
| Sign-up, resend verification, forgot password | 20 / hour (shared) | 5 / hour (shared) | Every request, whether or not the account exists |
| Reset-code check, reset with ticket | 30 / 15 min | 10 / hour | Every request |
| Email verification link `GET /auth/verify` | 30 / 15 min | — | Every request |

- **Response:** `429`, `Retry-After: <seconds>`, body `{"error":{"code":"RATE_LIMITED","message":"Too many
  attempts. Please wait and try again later."}}`; the same for every endpoint and whether or not the
  account exists. The web client shows that message (fallback added for proxies that answer `429`
  without the body).
- **No permanent lockout:** every window expires on its own; nothing is stored in the database.
- **Logging:** one `WARN` when a window fills — `Authentication rate limit reached for <bucket> per
  <client|account>` — with no address, email, code or password.
- **Memory:** at most `mednet.rate-limit.max-tracked-keys` (50 000) counters, least recently used
  evicted first; a few MB at most.
- **Settings:** `MEDNET_RATE_LIMIT_ENABLED` (default `true`; set `false` only for local load testing),
  `MEDNET_TRUSTED_PROXIES`, and individual limits as `mednet.rate-limit.<limit>.requests|window`
  (for example `mednet.rate-limit.email-delivery-per-account.requests=5`).
- **Several API instances:** each instance counts separately, so the effective allowance multiplies.
  Before scaling out, move the counters to shared storage or enforce equivalent limits at the edge
  proxy.

### Client addresses

The API uses `server.forward-headers-strategy: framework`, which makes `getRemoteAddr()` follow any
caller's `X-Forwarded-For`. The limiter therefore reads the container's own request (the TCP peer).
It honours `X-Forwarded-For` only when the peer is listed in `MEDNET_TRUSTED_PROXIES` (comma-separated
addresses or CIDR ranges), reading from the right and stopping at the first address that is not a
trusted proxy. Host names are never resolved. In production behind a reverse proxy, set
`MEDNET_TRUSTED_PROXIES` to the proxy's address range; otherwise all visitors share one allowance.

## Sessions

| Setting | Default | Variable |
|---|---|---|
| Idle timeout | 30 min | `SESSION_IDLE_TIMEOUT` |
| Absolute lifetime (from sign-in, however active) | 12 h | `SESSION_ABSOLUTE_TIMEOUT` (`0` disables) |

- Sign-in issues a new session ID (session-fixation protection), so the session's creation time is
  the sign-in time. `SessionLifetimeFilter` ends older sessions and the request continues signed out
  (`401` on protected endpoints; the web client then shows its sign-in prompt).
- **Confirmed defect, fixed:** sessions kept their old role and access after suspension, role change
  or password reset, until they timed out (and an active session never idles out). Now
  `AccountSessions` expires every session of the account on: password reset; administrator password,
  email or role change; suspension; verification withdrawn; deletion. The next request gets `401`.
  Both tests fail when this is switched off.
- Sign-out (`POST /auth/logout`, CSRF required) invalidates the session.
- No API contract changed: sign-in, sign-out and `/auth/session` behave as before; signed-out and
  expired sessions get the existing `401` body.

## Google OAuth

Reviewed; **end-to-end test blocked** (no development client: `GOOGLE_OAUTH_ENABLED=false`, no
client ID or secret). Do not enable Google sign-in in production until the steps below pass.

- **Client:** Google Cloud OAuth client of type *Web application*; scopes `openid`, `email`,
  `profile`; consent screen in testing mode with named test users for development.
- **Redirect URI:** exactly `GOOGLE_REDIRECT_URI`, `https://<public-origin>/api/v1/auth/oauth2/callback/google`
  in production (the proxy sends `/api/*` to the API); `http://localhost:8080/api/v1/auth/oauth2/callback/google`
  for a local API. Authorized JavaScript origins are not needed (server-side flow).
- **Settings:** `GOOGLE_OAUTH_ENABLED=true`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (secret store
  only), `GOOGLE_REDIRECT_URI`; a database must be configured.
- **Flow:** `GET /api/v1/auth/oauth2/authorization/google` → Google → callback. Spring Security checks
  `state` and the ID token (issuer, audience, signature from Google's keys, nonce).
- **Role landing pages:** `ADMIN`/`SUPER_ADMIN` → `/admin`, `PATIENT` → `/patient`, approved provider →
  `/provider`, `LABORATORY` → `/laboratory`, otherwise `/account`. Fixed paths, no redirect parameter.
- **Account linking:** Google must report `email_verified=true`; one Google identity per account and
  one account per identity; suspended accounts refused; staff and administrator roles kept; others
  become `PATIENT`, or `PROVIDER` with an approved application; an unconfirmed password is removed on
  first link (V2). Google sign-in counts as email verification.
- **Failures:** redirect to `/sign-in?error=google`; the log records only an error code or the
  policy reason, never tokens or addresses.
- **Account security:** the configured super administrator's address signs in as `SUPER_ADMIN` via
  Google, so that Google account needs 2-step verification and recovery controls equal to the admin
  password. Restrict the OAuth client's owners and rotate the client secret if it leaks.
- **End-to-end checklist:** sign in once as each of patient, approved provider, laboratory staff,
  administrator and super administrator and confirm the landing page; confirm a suspended account is
  refused; confirm a Google identity cannot link to a second account; confirm sign-out.

## Confirmed vulnerabilities and fixes

| # | Finding | Severity | Fix | Test |
|---|---|---|---|---|
| V1 | Sign-up replaced the password of an existing unverified account (staff-account takeover before verification) | High | Sign-up sets a password only on a new account | `signUpCannotReplaceThePasswordOfAnExistingUnverifiedAccount` |
| V2 | Google sign-in kept a password nobody had confirmed (pre-registration takeover) | High | Unconfirmed password removed on Google link | `GoogleOAuth2UserServiceTests` (2) |
| V3 | API allow-by-default | Medium | Deny by default | 31-endpoint test |
| V4 | Runtime dependencies with critical/high advisories | Critical | Spring Boot 4.1.1 and patch overrides | OSV scan |
| V5 | No web security headers | Low | `next.config.ts` headers | production check |
| V6 | No limits on sign-in, email-sending or reset-code endpoints (6-digit codes guessable at scale; email flooding) | High | Rate limiting (above) | `AuthenticationAbuseTests` (9 tests), `AuthRateLimiterTests` |
| V7 | Sessions survived suspension, role change and password reset | High | `AccountSessions` | 2 tests, fail without the fix |
| V8 | No absolute session lifetime | Medium | `SessionLifetimeFilter`, 12 h default | 1 test |

Behaviour changes to note: someone who signs up twice before verifying keeps the first password;
staff who first sign in with Google lose the administrator-set password; users are signed out after
12 hours and whenever an administrator changes their account's role, email, password or status.

## Verified controls (detail)

**Authentication:** BCrypt passwords of 12–72 UTF-8 bytes; unverified and suspended accounts cannot
sign in; one generic failure message. Email verification token 256-bit, SHA-256 stored, 24 h, cleared
on use. Reset code 6 digits, hashed, 10 min, cleared after 5 wrong tries, then a 256-bit single-use
ticket valid 10 min; a completed reset clears reset state, emails the owner and ends all sessions.
Configured super administrator credentials only from deployment settings.

**Authorization:** home-care staff see only requests assigned to them (care requested, visit location, status, visit time; never patient name, contact or records) and have no laboratory, patient or clinical endpoint; patient data only for the patient; providers need approval, a confirmed care
relationship and active consent (audited); appointment, consultation and message access only for the
two participants; laboratory staff see only assigned requests; patients see only released results;
administration needs `ADMIN`/`SUPER_ADMIN` with super-admin-only role, verification and profile edits;
no administrator clinical endpoint; preview roles are not real account types.

**Web and configuration:** CSRF cookie token on every state change including sign-in; no CORS (same
origin); `Cache-Control: no-store`, `nosniff`, `X-Frame-Options: DENY` on API responses; fixed error
messages with a request ID; logs carry no addresses, tokens, codes or clinical text; actuator `health`
only; API docs administrator-only; no `NEXT_PUBLIC_*` secrets; `.env*` git-ignored, `.env.example`
placeholders only. Session cookie `HttpOnly`, `Secure` outside local HTTP, `SameSite=Lax`.

## Dependency audit

| Area | Finding | Decision |
|---|---|---|
| Backend | 0 advisories across 154 resolved artifacts (OSV, rechecked in the closure) | Keep the `pom.xml` overrides until Spring Boot manages these versions |
| Web production | `npm audit --omit=dev`: 0 | — |
| Web development | 5 "high" findings = one advisory, GHSA-vfj7-8cjw-p6xm (`braces` ≤ 3.0.3, stack exhaustion from deeply nested glob patterns), via `eslint-config-next` → `@next/eslint-plugin-next` → `fast-glob@3.3.1` → `micromatch@4.0.8` → `braces@3.0.3` | **Deferred.** No patched `braces` exists (rechecked 2026-10-11); the newest plugin (16.4.0) still pins `fast-glob@3.3.1`; npm's fix is a downgrade to `eslint-config-next@14`, incompatible with Next 16. Exposure: only the linter on developer machines, fed glob patterns from the repository's own configuration; not in the production bundle or runtime. Recheck when `braces` or the plugin publishes a fix. |

## Checks and where they ran

| Check | Database / mode | Result |
|---|---|---|
| `mvn test` (71 tests, incl. 13 abuse/session and 5 limiter unit tests) | H2 | pass |
| Real-API browser suites (auth 30, admin 27, find-care, patient, provider, laboratory) | H2 API, `next dev` | 174/174 |
| Phase 5C: home-care authorization test, mocked spec, real-API suites incl. `home-care.live` | H2 / mocked | see `FRONTEND_PROGRESS.md` (Phase 5C) |
| Phase 5D: paged-endpoint authorization tests, `pagination.live` role checks, all real-API suites | H2 / mocked | pass (see `FRONTEND_PROGRESS.md`, Phase 5D) |
| Frontend lint, typecheck, Vitest (150), production build | mocks | pass |
| Mocked Playwright suite, production build, preview checks | mocked API / production build | pass (Phase 5B) |
| OSV (Maven), `npm audit` | — | backend 0; web production 0; development 5 (one advisory, deferred) |
| PostgreSQL integration tests and live suites | PostgreSQL | **blocked — credential rotation** |
| Google OAuth end to end | Google | **blocked — no OAuth client** |

## Phase 5E regression check (2026-10-12)

No backend or security-configuration change was made in Phase 5E. Verification, all without PostgreSQL:

| Check | Result |
|---|---|
| `mvn test` (H2) | **76/76**, 0 failures |
| Real-API suites on a fresh H2 API (rate limiting on, generated admin password, environment variables overriding every datasource value, so no connection with the exposed credential) | **206/206** across the eight live suites (role refusals, consent gating, laboratory release restriction, session end and sign-out, CSRF on every write); rate limits not tripped |
| Production-build headers | CSP `frame-ancestors 'none'; base-uri 'self'; object-src 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`; no `X-Powered-By` — unchanged |
| Production gating | `/foundation*` and `/preview/*` answer 404 by default |
| Client code | no browser storage of any kind, CSRF token on every write, session-expiry handling in every workspace, no `dangerouslySetInnerHTML`/`eval` |
| Frontend fixes with a security angle | none needed; the Phase 5E dialog fix (`FINAL_FRONTEND_QA.md` §5) changes only when the parent is told a dialog closed |

Launch blockers 1–5 above are unchanged. The production CSP still omits `script-src`/`connect-src`, and HSTS is
expected from the reverse proxy; both remain deployment decisions (`FINAL_FRONTEND_QA.md` §11).
