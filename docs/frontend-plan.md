# MedNet Frontend Plan

This document is the dedicated frontend plan for MedNet. It complements the product requirements in [req.md](req.md) and focuses only on the user experience, routes, frontend architecture, responsiveness, accessibility, and product UX flow. See the [Production Architecture Guide](PRODUCTION_ARCHITECTURE_GUIDE.md) for the full-stack boundary and deployment model.

---

## 1. Product-facing direction

The frontend should be built as a mobile-first, low-bandwidth, accessibility-conscious product for Liberia. The app should be usable in a constrained mobile environment and should support patient, provider, and administrator use cases without requiring a desktop-only experience.

### Recommended default frontend

- Next.js App Router
- TypeScript
- Tailwind CSS
- route groups and layout shells
- semantic design tokens
- strong accessibility defaults
- dark/light mode support

This supports the SEO and public-discovery needs in the supplied recommendation while allowing authenticated user flows in the same web application. It is a provisional web candidate only; OQ-50 remains open until the client confirms web, native mobile, or both.

---

## 2. Architectural direction

### Suggested route structure

```text
app/
├── (public)/
│   ├── page.tsx
│   ├── about/page.tsx
│   ├── services/page.tsx
│   ├── providers/page.tsx
│   ├── health-resources/page.tsx
│   ├── contact/page.tsx
│   └── faq/page.tsx
├── (auth)/
│   ├── login/page.tsx
│   ├── register/page.tsx
│   ├── forgot-password/page.tsx
│   └── onboarding/page.tsx
├── (patient)/
│   ├── dashboard/page.tsx
│   ├── appointments/page.tsx
│   ├── messages/page.tsx
│   ├── records/page.tsx
│   ├── medication/page.tsx
│   ├── vitals/page.tsx
│   ├── home-care/page.tsx
│   └── lab-requests/page.tsx
├── (provider)/
│   ├── dashboard/page.tsx
│   ├── appointments/page.tsx
│   ├── patients/page.tsx
│   ├── records/page.tsx
│   └── availability/page.tsx
├── (admin)/
│   ├── dashboard/page.tsx
│   ├── providers/page.tsx
│   ├── reports/page.tsx
│   └── audit/page.tsx
```

### Why this structure works

- It separates public, auth, patient, provider, and admin flows cleanly.
- It keeps route shells simple and consistent.
- It makes role-specific security easier to enforce.
- It reduces layout duplication.

Public pages should provide appropriate metadata and be indexable where intended. Authenticated pages must validate the session before rendering protected content and must not expose patient data through public metadata or shared caches.

The authoritative `/api/v1` API belongs to Spring Boot. The browser may call it through the agreed same-domain HTTPS reverse-proxy route. Next.js route handlers, if introduced, are not a substitute for backend authentication, relationship authorization, or clinical business rules.

---

## 3. Frontend naming conventions

Use the same naming philosophy as the backend:

- Keep full names where they are already clear: `patient`, `provider`, `record`, `appointment`, `document`, `schedule`.
- Abbreviate only where it makes repeated code cleaner: `auth`, `appt`, `msg`, `med`, `notif`, `admin`, `dto`, `svc`.
- Use clear folder names for routes and feature modules.
- Keep reusable UI in shared components rather than repeating page-specific markup.

### Recommended feature folders

- `components/auth`
- `components/patient`
- `components/provider`
- `components/admin`
- `components/common`
- `components/ui`
- `lib/api`
- `lib/validation`
- `hooks/useAuth`
- `hooks/useAppointments`
- `hooks/useNotifications`

---

## 4. UX standards for MedNet

### Core UX principles

- mobile-first design
- simple, plain-language actions
- clear status indicators
- large touch targets
- low-data load strategy
- one primary action per screen
- strong patient guidance and confidence building

### Required states for every workflow

Every critical MedNet flow should include:

- loading state
- empty state
- success state
- error state
- recovery guidance

Examples:

- patient registration form
- appointment booking flow
- message inbox
- lab request submission
- provider approval queue
- medication reminder list

---

## 5. Main user journeys

### Patient journey

1. create account or sign in
2. search and locate a provider
3. book or request an appointment
4. receive confirmation and reminders
5. review record history and medication schedule
6. add vitals and request home care or lab services
7. receive notification updates

### Provider journey

1. sign in and access dashboard
2. review appointment requests
3. accept or reschedule appointments
4. reply to patient messages
5. review patient record and add consultation notes
6. manage availability and provider profile

### Admin journey

1. log in to admin dashboard
2. review pending providers
3. approve or reject provider applications
4. monitor requests and status changes
5. review platform summaries and audit events

---

## 6. Frontend security boundaries

- never render patient data without a validated session and authorization check
- do not expose tokens or secrets in client code
- keep patient and provider dashboards separated by route and access rules
- all mutating actions should call the server and trust server-side validation
- show user-friendly messages; do not leak internal technical errors

---

## 7. Production-ready frontend quality checks

The frontend should satisfy the following before release:

- responsive across small mobile screens
- accessible keyboard navigation and screen-reader labels
- clear error states and validation messages
- minimal network load for repeated actions
- low-bandwidth-friendly image and asset handling
- distinct patient, provider, and admin flows
- working offline-safe patterns where viable

---

## 8. Recommended frontend implementation priorities

1. authentication screens
2. patient dashboard and provider directory
3. appointment booking and status tracking
4. notification inbox and reminders
5. record overview and medication flow
6. vitals capture
7. homecare and lab request flows
8. admin dashboard and reporting

This order matches the product requirements and keeps the product usable before deeper clinical workflows are finalized.

---

## 9. Short naming conventions for frontend code

Use the same short-name discipline as the backend:

- `auth` for login and access code
- `appt` for appointment logic and components
- `msg` for message UI
- `med` for medications
- `notif` for notifications
- `admin` for admin screens and controls
- `svc` for service-layer utilities only when repeated across pages

But keep these names full in user-facing code when the context is direct and readable:

- `patient`
- `provider`
- `record`
- `appointment`
- `schedule`
- `laboratory`
- `homeCare`

This keeps the product UI understandable to designers, clinical stakeholders, and engineers.

---

## 10. Final recommendation

The frontend should be built around a clear patient/provider/admin structure, a fast low-data UX, and a very strict security boundary. The product should start with the highest-value flows and expand carefully as the clinical and regulatory decisions become confirmed.

For the implementation roadmap, the strongest default is:

- mobile-first web app
- modular route groups
- shared API client and validation patterns
- accessible design system
- server-side authorization enforced on all protected content

This gives MedNet a clean, maintainable frontend foundation without overbuilding before the client confirms the final platform strategy.
