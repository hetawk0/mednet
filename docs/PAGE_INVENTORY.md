# MedNet Page Inventory

Reference: https://dab-text-35201810.figma.site (verified 2026-10-10 at 1440×900, 820×1180 and 390×844).

The reference is a single-page prototype with a floating switcher between five views. It has no URLs or
routes. Public-header links ("Find care", "How it works", "For providers", "Help", "Sign in") do nothing in
the prototype. Routes below are therefore **proposed**, not taken from the design. Workspace routes are
recorded in code in `web/src/features/workspace/workspaces.ts`, which is the source of truth for navigation.

Scope rule (approved 2026-10-10): every designed screen stays in the roadmap. Where the backend has no
capability, including payments, which are out of scope per `AGENTS.md`, the screen is built
**presentation-only** with clearly labelled fictional data and no invented endpoints.

Status key: **Designed** = full bespoke screen in reference · **Template** = generic sub-view (header,
"Current view" list of 3–4 items, privacy card) · **None** = no reference design.

## 1. Reference screens

### Public
| Screen | Reference | Proposed route | Existing code | Backend |
|---|---|---|---|---|
| Homepage | Designed | `/` | ✅ 4B: `features/home/*` to reference; live specialties and providers | `GET /providers`, `GET /providers/{id}/availability` |
| About, Services | None (footer links only) | `/about`, `/services` | ✅ 4B: interpreted with `PageIntro` + cards (`DESIGN_SYSTEM.md` §14) | — |
| For providers, Help, Contact, Privacy, Terms, Accessibility | None | `/for-providers`, `/help`, `/contact`, `/privacy`, `/terms`, `/accessibility` | ✅ 2026-10-17: placeholder pages (`features/public/pending-page.tsx`) stating content is pending approval; no invented content | — |
| Doctor discovery / booking flow | Component-level preview only (DS → Components) | `/find-care` | ✅ 4B: `features/find-care/*` (search, open-times drawer, patient request) | `GET /providers`, `GET /providers/{id}/availability`, `POST /appointments` |

### Auth
| Screen | Reference | Existing code |
|---|---|---|
| Sign in, register, verify email, reset password, account | **None** (interpreted, `DESIGN_SYSTEM.md` §13) | ✅ 4A: `app/(auth)/{sign-in,verify,reset,account}` → `features/auth/*` panels on `AuthShell`, `lib/api`; verified against a running API |

### Patient workspace (sidebar: Overview, Find a doctor, Appointments, Messages, Medical information, Payments, Help & support, Public website)
| Screen | Reference | Proposed route | Existing code | Backend |
|---|---|---|---|---|
| Overview | Designed | `/patient` | ✅ 4C `features/patient/overview.tsx` | `/appointments`, `/lab-requests`, `/home-care-requests`, `/notifications` |
| Find a doctor | Template | `/patient/find-a-doctor` | ✅ 4C reuses `FindCare` (`basePath`) | `/providers` |
| Appointments | Template | `/patient/appointments` | ✅ 4C `appointments.tsx` (details drawer, actions) + home-care tab | `/appointments`, `/home-care-requests` |
| Text consultation | None | `/patient/appointments/consultation` | ✅ 4C `consultation.tsx` (join on request) | `/appointments/{id}/consultation`, `/consultations/{id}` |
| Messages | Template | `/patient/messages` | ✅ 4C `messages.tsx` | `/conversations` |
| Medical information | Template | `/patient/records` | ✅ 4C `records.tsx` (records, laboratory, medications, vitals, sharing) | `/patients/me/records`, `/lab-results`, `/medications`, `/vitals`, `/record-consents` |
| Payments | Template | `/patient/payments` | ✅ 4C presentation-only (`FictionalDataBadge`) | **none** — presentation-only (payments out of scope) |
| Help & support | Template | `/patient/help` | ✅ 4C presentation-only, factual topics | none — presentation-only |
| Profile | None | `/patient/profile` | ✅ 4C `profile-form.tsx` | `/patients/me/profile` |

### Doctor workspace (sidebar: Overview, Schedule, Appointments, Patients, Clinical work, Messages, Earnings & payouts, Help & support)
| Screen | Reference | Proposed route | Existing code | Backend |
|---|---|---|---|---|
| Overview | Designed | `/provider` | ✅ 4D `features/provider/overview.tsx` | `/appointments`, `/providers/me/application`, `/patients/{id}/lab-results/pending` |
| Schedule | Template | `/provider/schedule` | ✅ 4D `schedule.tsx` (publish, close; no edit/recurrence in API) | `/providers/me/availability`, `/providers/{id}/availability` |
| Appointments | Template | `/provider/appointments` (+ `/consultation`) | ✅ 4D `appointments.tsx`, `consultation.tsx` | `/appointments`, `/appointments/{id}/consultation`, `/consultations/{id}` |
| Patients | Template | `/provider/patients` (+ `/[id]`) | ✅ 4D `patients.tsx` | `/patients/{id}/records` (consent-gated) |
| Clinical work | Template | `/provider/clinical-work` | ✅ 4D `clinical-work.tsx` (results queue assembled per patient) | records, lab review `PATCH /lab-results/{id}/review` |
| Messages | Template | `/provider/messages` | ✅ 4D `messages.tsx` (reply only; patients start) | `/conversations` |
| Earnings & payouts | Template | `/provider/earnings` | ✅ 4D presentation-only (`FictionalDataBadge`) | **none** — presentation-only |

### Operations (role switcher with 9 workspaces; each has a numbered submenu that swaps to a generic list + "Selected record" detail)
| Workspace | Reference | Backend role | Existing code |
|---|---|---|---|
| Laboratory `/laboratory` (Overview, Orders, Specimens, Results, Test catalogue, Pricing & payouts, Audit history; + `/profile`) | Designed | `LABORATORY`: `/partner/service-requests` (list, OPEN→IN_PROGRESS) and `POST …/lab-result` (PENDING_REVIEW). Specimens, catalogue, pricing and audit have no API | ✅ 4F `features/laboratory/` — Specimens/catalogue/pricing/audit presentation-only; Results limited to request statuses (no result read-back API) |
| Home care `/home-care` (Overview, Visits, Help and guidance; + `/profile`) | **Not designed** (Phase 5C interpretation of the laboratory layout, `DESIGN_SYSTEM.md` §20) | `HOME_CARE`: `GET /partner/service-requests?type=HOME_CARE`, `PATCH …/{id}/schedule` (once, future, OPEN), `PATCH …/{id}/status` (scheduled OPEN→IN_PROGRESS→RESOLVED) | ✅ 5C `features/home-care/` — all views use the real API |
| Collection provider `/preview/collection` (Overview, Pickup requests, Assignments, Tracking, Exceptions, Pricing & payouts, History) | Designed | **no role or API** | ✅ 4G presentation-only (`features/preview/`) |
| Collection agent `/preview/collection-agent` (Today, Assigned pickups, Offline queue, Handoff history), mobile-first | Designed | **no role or API** | ✅ 4G presentation-only |
| Super administrator `/admin` (Overview, Users and roles, Providers, Verification, Security, Configuration; plus Task queue, Appointments) | Designed | `SUPER_ADMIN`: every admin endpoint incl. account edit/verification/delete | ✅ 4E `features/admin/` — Verification = email verification; Security = audit trail; Configuration read-only |
| Administrator `/admin` (Overview, Task queue, Onboarding, Appointments, Escalations, Reports) | Designed | `ADMIN`: providers, accounts (create/suspend), requests, service requests, appointments, audit | ✅ 4E `features/admin/` — Escalations presentation-only; Reports = live counts + audit |
| Customer support `/preview/support` (Overview, Ticket queue, User lookup, Templates, Escalations, Reports) | Designed | **no role, no ticket API** | ✅ 4G presentation-only |
| Financial officer `/preview/finance` (Overview, Transactions, Refunds, Reconciliation, Payouts, Reports) | Designed | **no role; payments out of scope** | ✅ 4G presentation-only |
| Specimen lifecycle `/preview/specimen-lifecycle` (Workflow map, Exception paths, Dependencies) | Designed (reference/explainer) | n/a | ✅ 4G presentation-only, each stage marked with MedNet support |
| Permissions matrix `/preview/permissions-matrix` (Matrix, Access principles, Audit requirements) | Designed (reference/explainer) | n/a | ✅ 4G presentation-only proposal + factual "enforced today" list |

### Design system
Foundations, Components, Patterns, Usage guide — documentation views; captured in `DESIGN_SYSTEM.md`.
Not proposed as product routes.

## 2. Existing routes with no reference design
`/about`, `/services`, `/sign-in`, `/verify`, `/reset`, `/account`, `/admin`, `/patient/profile`.
Approved: restyle with the reference tokens, components and patterns only. Every interpreted layout is
logged in `DESIGN_SYSTEM.md` §10 and `FRONTEND_PROGRESS.md`.

Current route organisation (Phase 2, URLs unchanged):

| Group | Routes | Layout |
|---|---|---|
| `(public)` | `/`, `/find-care`, `/about`, `/services` | `PublicHeader` + `PublicFooter`; bodies rebuilt in 4B |
| `(auth)` | `/sign-in`, `/verify`, `/reset`, `/account` | `AuthShell` (4A, done) |
| `patient/` | `/patient/*` (9 routes) | ✅ 4C: `patient/layout.tsx` → `PatientWorkspace` (guard + `WorkspaceShell`) |
| `admin/` | `/admin/*` (11 routes) | ✅ 4E: `admin/layout.tsx` → `AdminWorkspace` (role-chosen menu + `WorkspaceShell`) |
| `laboratory/` | `/laboratory/*` (8 routes) | ✅ 4F: `laboratory/layout.tsx` → `LaboratoryWorkspace` (LABORATORY guard + `WorkspaceShell`) |
| `home-care/` | `/home-care`, `/home-care/visits`, `/home-care/help`, `/home-care/profile` | ✅ 5C: `home-care/layout.tsx` → `HomeCareWorkspace` (HOME_CARE guard + `WorkspaceShell`) |
| `(preview)` | `/preview/*` (15 routes) | ✅ 4G: presentation-only; off in production unless `MEDNET_PREVIEW_WORKSPACES=enabled`; no session or API |
| `(dev)` | `/foundation`, `/foundation/workspace/[id]` | development-only, 404 in production |

## 2a. Screen-to-component map (Phase 3)

Components for every designed screen now exist (see `DESIGN_SYSTEM.md` §12). Phase 4 composes them;
it does not need new primitives except where noted.

| Screen | Components |
|---|---|
| Public homepage | `PublicHeader`, `SectionHeading`, `Card`, `DoctorCard`, `ButtonLink`, `PublicFooter`; hero search uses `SearchInput`/`SelectField` (**needs** date/availability selector, Phase 4B) |
| Doctor discovery / booking | `FilterBar`, `SelectField`, `DoctorCard`, `Stepper`, `Badge`; date/time selectors (Phase 4B) |
| Patient overview | `WorkspaceShell`, `WelcomeBand`, `AppointmentSummary`, `ActivityList`/`ActivityRow` ("For you"), `MedicalRecordList`, `Card`, `Alert` (success, "protected"), `StateMessage` |
| Patient payments (presentation-only) | `Card`, `DescriptionList`, `Badge`, `FictionalDataBadge` |
| Doctor overview | `WorkspaceShell`, `WelcomeBand`, `MetricGrid`, schedule rows via `ActivityRow` + `AppointmentStatusBadge`, `ActivityList` (clinical attention), `Alert`, `Card`; earnings presentation-only |
| Template sub-views (patient and doctor) | `WorkspaceShell`, `SectionHeading`, `ActivityList`, `Card`, `Alert` |
| Laboratory | `MetricGrid`, `FilterBar`, `DataTable`, `PatientSummary`, `StatusTimeline` (horizontal), `Checkbox` (receipt checks), `RestrictedNotice`, `ServiceRequestStatusBadge` / `LabResultStatusBadge` |
| Collection provider / agent (presentation-only) | `DataTable`, `ActivityList`, `PatientSummary`, `SelectField`, `Stepper`, `StatusTimeline`, `FictionalDataBadge` |
| Super admin / Administrator | `MetricGrid`, `ActivityList`, `AuditEvent`, `PermissionBoundary`, `ConfirmDialog` (high-impact actions), `DataTable`, `Pagination`, `Menu` |
| Customer support (presentation-only) | `DataTable`, `ActivityList`, `TextArea`, `Menu` ("Escalate to…"), `RestrictedNotice` |
| Financial officer (presentation-only) | `MetricGrid`, `DataTable`, `DescriptionList`, `InfoTip` ("Rate not displayed"), `AuditEvent`, `RestrictedNotice` |
| Specimen lifecycle / Permissions matrix | `StatusTimeline`, `Card`, `DataTable` |
| Auth pages (no reference design) | `AuthShell`, `Card`, `SegmentedControl`, `TextInput`, `PasswordField`, `Stepper`, `Button`/`ButtonLink`, `Alert`, `StateMessage`, `DescriptionList` |

## 3. Capture files
Screenshots and rendered text from the audit are in the session scratchpad (not committed): home, patient,
doctor, each sidebar sub-view, all nine operations workspaces, four design-system sections, plus
390px and 820px captures of homepage and both dashboards.

## 4. Reference behaviour observed at smaller widths
- 820px: homepage and dashboards fit (no horizontal scroll).
- 390px: homepage and patient dashboard fit; sidebar collapses to an icon rail.
- **Defect:** doctor dashboard overflows at 390px (scroll width 491px).
- **Defect:** primary/secondary buttons clip their labels at 390px ("Prepare for visit", "View details").
- **Defect:** dashboard sidebar background ends at the first viewport height on long pages.
These will be fixed in implementation rather than reproduced, and logged as deviations.
Phase 2 status: the sidebar height and the 820px squeeze are fixed in `WorkspaceShell` (verified by
Playwright). Button clipping is fixed in the Phase 3 `Button` (labels wrap; verified at 390px). The doctor overflow
will be fixed when the doctor overview is built in Phase 4D.

## 5. Classification of every screen (end of Phase 4)

| Area | Functional (real API) | Partially functional | Presentation-only |
|---|---|---|---|
| Public | home, find care (directory + booking), about, services | — | — |
| Account access | sign-in, register, verify, reset, account | MedNet API session, verification, and password recovery | — |
| Patient | overview, find a doctor, appointments, messages, consultation, medical information, profile | help (factual guidance) | payments |
| Doctor | overview, schedule, appointments, consultation, patients, messages, profile | clinical work (results queue per patient) | earnings & payouts |
| Super admin / admin | overview, users and roles, providers, verification, security, task queue, onboarding, appointments, reports | configuration (read-only status) | escalations |
| Laboratory | overview, orders, results, profile | — | specimens, test catalogue, pricing & payouts, audit history |
| Home care (5C) | overview, visits, profile | help (factual guidance) | — |
| Operations previews | — | — | collection provider, collection agent, support, finance, specimen lifecycle, permissions matrix |

## 6. Route audit (Phase 5E, 2026-10-12)

Every route above was reviewed; results in `FINAL_FRONTEND_QA.md` §2 and §7.

- **Sweep coverage:** `web/e2e/audit.spec.ts` visits 74 route states (every page route, tabs and detail
  views included, plus an unknown address) at 390, 820 and 1440px, and again at 320px for reflow.
- **Production gating** (`next start`, default configuration): `/foundation`, `/foundation/workspace/*` and
  every `/preview/*` route answer 404; unknown addresses answer 404 with the branded page (`app/not-found.tsx`,
  new in 5E, no reference design; built from the public shell).
- **Links:** every internal link on every route state resolves to a route in this inventory; the only API
  link is the MedNet sign-in page.
- **No route changes** in Phase 5E: no page was added, removed or renamed apart from the 404 page.
