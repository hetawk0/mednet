# MedNet Design System (implementation reference)

Source of truth: the approved Figma Make prototype at https://dab-text-35201810.figma.site
("Clinical Calm", design system v0.1, marked *provisional foundations*).

Everything below was verified on 2026-10-10 by rendering the reference in a headless browser,
reading its rendered text, screenshots and compiled CSS. Values marked **(stated)** come from the
reference's Design system page; values marked **(CSS)** come from its compiled stylesheet. Where the
two disagree, the conflict is listed under *Open design questions* and is not silently resolved.

## 1. Colour tokens

| Token (CSS) | Value | Stated role |
|---|---|---|
| `--green-950` | `#0B3127` | Brand 950 · dark brand surfaces (sidebar, trust band) |
| `--green-900` | `#104C3B` | Brand 900 · dashboard emphasis (hero card) |
| `--green-800` | `#126048` | Hover/pressed primary (CSS only) |
| `--green-700` | `#147453` | Brand 700 · primary action, success |
| `--green-100` | `#DCEFE7` | Brand 100 · selected surface, avatar fill |
| `--green-50` | `#EDF7F3` | Brand 50 · subtle surface |
| `--ink` | `#17231F` | Neutral 950 · primary text |
| `--ink-soft` | `#50605A` | Neutral 600 · secondary text |
| `--ink-faint` | `#64716C` | Neutral 400 · muted text |
| `--line` | `#DCE4E0` | Neutral 200 · borders |
| `--line-soft` | `#E9EEEB` | Row dividers (CSS only) |
| `--control-border` | `#AEBBB5` | Form control borders (CSS only) |
| `--surface` | `#FFFFFF` | Cards |
| `--surface-soft` | `#F6F8F7` | Neutral 50 · canvas |
| `--surface-warm` | `#F6F4EE` | "Three steps" band (CSS only) |
| `--blue` / `--blue-soft` | `#336A82` / `#E8F2F6` | Info 700 · information |
| `--amber` / `--amber-soft` | `#945B17` / `#FBF1DD` | Warning 700 · warning, "Doctor review" |
| `--red` / `--red-soft` | `#A84343` / `#F9EAEA` | Critical 700 · error / urgent |
| `--focus-ring` | `rgba(20,116,83,.28)` | Focus ring |

Semantic aliases (stated): `action/primary → --green-700`, `text/primary → --ink`,
`surface/canvas → --surface-soft`, `border/default → --line`, `focus/ring → --focus-ring`.
Usage rule (stated): components reference semantic tokens, never raw palette values. Status must never
rely on colour alone (text + icon always accompany status colour).

## 2. Typography

- Family: **Source Sans 3** (stated provisional), fallback `Arial, sans-serif`. Loaded from Google Fonts in
  the reference; implement with `next/font/google` (`Source_Sans_3`).
- Scale (stated):

| Style | Size / line height | Weight |
|---|---|---|
| Display | 56 / 60 | Semibold |
| Heading 1 | 40 / 48 | Semibold |
| Heading 2 | 28 / 36 | Semibold |
| Body | 16 / 24 | Regular |
| Data | 14 / 20 | Semibold, tabular numerals |

- Eyebrows: uppercase, letter-spaced, small, green or ink-soft ("HEALTHCARE, MADE CLEARER", "NEXT APPOINTMENT").

## 3. Spacing, grid and breakpoints

- 4-point base; preferred layout gaps 16 / 24 / 32 (stated). Scale shown: 4, 8, 12, 16, 24, 32, 40, 48, 64.
- Grid (stated): Compact 320–767 → 4 cols, 16px margins · Medium 768–1199 → 8 cols, 24px · Wide 1200–1439 →
  12 cols, 32px · Extra wide 1440+ → 12 cols, max content 1480px.
- Breakpoints actually used in the reference CSS: 520, 760, 820, 1050, 1200px (see open questions).

## 4. Shape and elevation

- Radius (stated): small 8px (controls), medium 12px (cards), round 999px (status pills only).
  Reference CSS also uses 7, 9, 10, 11, 13 and 20px — treated as drift, normalised to the stated values.
- Elevation (stated): borders and spacing do structural work; elevation only for overlays, floating controls,
  active selection. CSS: `--shadow-sm: 0 1px 2px #0f31260d`, `--shadow-md: 0 18px 46px #0f31261a`.
- Avatars: circular, initials on `--green-100`.

## 5. Iconography and imagery

- 1.8px outline icons, consistent set (stated). The reference draws inline SVGs; no icon package is
  identifiable. **Decision (2026-10-10): Lucide React**, always rendered with `strokeWidth={1.8}`.
- Workspace navigation icon mapping (in `features/workspace/workspaces.ts`):

  | Item | Reference glyph | Lucide | Note |
  |---|---|---|---|
  | Overview | house | `House` | match |
  | Find a doctor | heart | `Heart` | match |
  | Appointments | calendar | `CalendarDays` | match |
  | Messages | message square with lines | `MessageSquareText` | match |
  | Medical information | heart (same as Find a doctor) | `HeartPulse` | **deviation:** the reference reuses one heart for three items; distinct glyphs aid recognition |
  | Payments | heart (same) | `ReceiptText` | **deviation:** as above |
  | Help & support | shield | `ShieldCheck` | match |
  | Public website | arrow right | `ArrowRight` | match |
  | Doctor: Schedule, Patients, Clinical work, Earnings | not captured in detail | `CalendarClock`, `Users`, `Stethoscope`, `WalletCards` | interpretation; review against Figma |
  | Operations items | numbered `01`–`07` | — | match (numbers, no icons) |

- Photography: authentic healthcare photography with descriptive alt text.
  **Hero image (provisional):** `web/public/images/hero-care.jpg`, 1400×933, downloaded on 2026-10-10 from
  `https://images.unsplash.com/photo-1666886573553-6548db92db79` (the image the reference uses; alt text
  "A doctor discussing care with a patient using a tablet"). It is self-hosted so the site has no runtime
  dependency on Unsplash. **Licensing:** the standard `images.unsplash.com/photo-…` path indicates the
  Unsplash License (free commercial use, attribution appreciated but not required, no selling unaltered
  copies, no use to build a competing service), not Unsplash+. The photographer and licence could not be
  verified automatically (Unsplash blocked the request). Confirm the photo page, record the
  photographer's credit, and confirm the people depicted are appropriate for MedNet's use before
  production.

## 6. Accessibility baseline (stated, WCAG 2.2 AA)

Contrast 4.5:1 body / 3:1 large text and UI boundaries · visible 3px focus ring with ≥2px separation ·
targets 44×44 patient-facing, 40×40 professional · no loss at 200% text zoom · text + icon with every status
colour · reduced-motion support.

## 7. Component inventory (named in the reference "Initial component library")

| Group | Components |
|---|---|
| Actions | Button/Primary, Button/Secondary, Button/Tertiary, Button/Destructive (all Medium), Icon button/Neutral, Link/Inline, Segmented control |
| Forms | Input/Text (default, error), Input/Search, Select, Checkbox, Radio, Switch, Textarea, Upload/Document |
| Navigation | Public header, Dashboard sidebar, Mobile navigation, Breadcrumb, Tabs/Underline, Pagination |
| Healthcare | Card/Doctor profile, Card/Appointment, Badge/Appointment, Selector/Date, Selector/Time, Result/Review status, Timeline/Specimen handoff |
| Operations | Table/Data, Filter bar, Menu/Action, Metric card, Activity list, Queue item, Audit event |
| Feedback | Alert/Information, Alert/Warning, Alert/Critical, Toast/Success, Dialog/Confirmation, Drawer/Detail, State/Empty, State/Error, Skeleton/Card |

Rendered previews verified: buttons (primary, secondary, tertiary, destructive "Cancel appointment",
disabled), form fields with helper and error text, checkbox/radio/switch, status badges (Confirmed,
Available, Doctor review, Action required), three alert tones, doctor card, appointment card, specimen
timeline, 6-step booking progress (Find care → Profile → Schedule → Sign in → Review → Confirmed), doctor
discovery filter + results.

Every workspace in the reference also shows a shared state strip: *Loading*, *Empty*, *Error (retry)*,
*Success*, *Permission denied*.

## 8. Patterns (stated)

Search and filter · Profile and detail · Multi-step form · Appointment booking · Payment summary ·
Medical record · Doctor schedule · Specimen handoff · Approval queue. Rule: visual structure is shared across
roles; permissions, data exposure and actions are not.

## 9. Decisions (approved 2026-10-10)

| Question | Decision |
|---|---|
| Breakpoints | Stated 768 / 1200 / 1440px (`md` / `lg` / `xl`). Below 768px is the compact default. |
| Radii | Stated 8px (`rounded-sm`) and 12px (`rounded-md`); `rounded-full` for status pills and avatars only. |
| Brand green | Keep the reference values (`#147453` primary etc.) until the owner approves a change. |
| Icons | Lucide React, 1.8px stroke (§5). |
| Hero photo | Keep provisionally, self-hosted; licensing to confirm (§5). |
| Reference defects | Fix, don't reproduce (see `PAGE_INVENTORY.md` §4 and §10 below). |
| Payments / unsupported roles | Keep designs; presentation-only with fictional data (§11). |

## 10. Implementation (Phase 2)

Tokens are defined once in `web/src/app/globals.css` inside Tailwind 4's `@theme`. Tailwind's default
colour palette, radii, shadows and breakpoints are cleared (`--color-*: initial` etc.) so only approved
values exist as utilities.

| Token group | CSS variable | Utility examples |
|---|---|---|
| Palette | `--color-green-700`, `--color-ink-soft`, `--color-warning-soft`… | `bg-green-950`, `text-ink-soft` |
| Semantic | `--color-action`, `--color-action-hover`, `--color-text-secondary`, `--color-canvas`, `--color-border`, `--color-sidebar`, `--color-focus-ring`… | `bg-action`, `hover:bg-action-hover`, `text-text-secondary`, `border-border`, `bg-sidebar` |
| Type | `--text-display`, `--text-h1`, `--text-h2`, `--text-body`, `--text-data`, `--text-caption`, `--text-eyebrow` (each with line height) | `text-h1`, `text-data` |
| Font | `--font-sans` → Source Sans 3 via `next/font` (`--font-source-sans`) | `font-sans` (default) |
| Radius | `--radius-sm` 8px, `--radius-md` 12px, `--radius-full` | `rounded-sm`, `rounded-md` |
| Shadow | `--shadow-card`, `--shadow-overlay` | `shadow-card`, `shadow-overlay` |
| Breakpoints | `--breakpoint-md` 48rem, `--breakpoint-lg` 75rem, `--breakpoint-xl` 90rem | `md:`, `lg:`, `xl:` |
| Layout | `--container-page` 77.75rem (1180px content + 32px gutters; was 77.5rem until 2026-10-17), `--container-content` 1480px, `--width-sidebar` 15.75rem, `--width-sidebar-rail` 4.5rem | `max-w-page` |

Global behaviour:

- **Focus:** `:focus-visible` uses a 3px `--color-action` outline with a 2px offset. **Deviation:** the
  reference's `focus/ring` value (`rgba(20,116,83,.28)`) is too faint to meet the 3:1 non-text contrast
  it states. It is kept as the `focus-ring` token for halos, not as the only focus indicator.
- **Reduced motion:** animations and transitions are neutralised under `prefers-reduced-motion`.
- **Legacy styles:** removed in Phase 5D (see §21). Only three global element rules remain in
  `@layer components`: links inherit colour without underline, links get a 3px `green-700` focus outline
  offset 4px, and `h1`–`h3`/`p` wrap long words.

Shared layouts (`web/src/components/layout`):

| Component | Reference | Interpretation or deviation |
|---|---|---|
| `PublicHeader` | Navigation/Public header | **Interpretation:** a menu toggle below 1200px (where the reference hides the four links with no replacement), so navigation and, on phones, Sign in stay reachable. All four reference links are rendered (2026-10-17); "For providers" and "Help" go to placeholder pages (§14.4). Below 1200px the reference left-aligns Sign in/Book beside the wordmark; they stay right-aligned. "Book an appointment" goes to `/find-care`. |
| `PublicFooter` | Public footer | All reference columns and links are rendered (2026-10-17); links without approved content go to placeholder pages (§14.4). The "Prototype content is fictional" note is dropped on the live site. Bottom padding is 56px, not 95px: the reference's extra space only clears the prototype's floating screen switcher. |
| `WorkspaceShell` | Navigation/Dashboard sidebar + top bar | **Fixes:** the sidebar is sticky and fills the viewport height. **Responsive adjustment:** a 72px icon rail below 1200px (the reference keeps the full 244px sidebar at 820px, squeezing content). The wordmark is white on the sidebar; the reference's dark-green-on-dark-green patient wordmark fails contrast. The prototype's "Switch workspace" select is not built, because the backend gives each account one role. |
| `Wordmark` | MedNet logotype | Text wordmark only; "M" in the collapsed rail. Dark tone is green-900 with −0.04em (−1px) tracking, as measured in the public header and footer (2026-10-17). |
| `FictionalDataBadge` | "Fictional data" chip | Required on every presentation-only screen. |

## 11. Presentation-only data convention

Approved designs whose backend does not exist (payments, payouts, collection provider/agent, customer
support, financial officer, parts of laboratory) may be built with fictional data:
`src/features/<domain>/fictional.ts`, wrapped with `fictional()` from `src/lib/fictional.ts`, and always
displayed with `<FictionalDataBadge />`. They must never call invented endpoints or be described as
integrated. The workspace registry records each view's `backend` support (`supported` / `partial` / `none`).

## 12. Component library (Phase 3)

Code lives in `web/src/components/`:

- `ui/`: generic primitives with no domain knowledge.
- `healthcare/`: domain components. They render data they are given and never fetch.
- `workspace/`: workspace compositions.
- `layout/`: the shells from Phase 2.

Every component uses semantic tokens only. The live catalogue is `/foundation`, which is
development-only and returns 404 in production.

### 12.1 Reference measurements (2026-10-10, 1440px, computed styles)

| Element | Reference value | Implemented |
|---|---|---|
| Button | 44px min height, 20px inline padding, 15px/700 label, 9px radius, 1px border, card shadow on primary | same; radius 8px token |
| Button colours | primary `#147453`/white; secondary white, border `#B9D2C8`, text `#126048`; tertiary transparent, `#126048`; destructive white, border `#E0B7B7`, text `#A84343` | same (`action-border`, `critical-border` tokens added) |
| Disabled | opacity 0.42, `not-allowed` | same |
| Text field / select / textarea | 45px, 10/12px padding, 1px `#ADBBB5` border, 8px radius, 13px/600 value; error: `#A84343` border + 1px ring | same, but value text 16px (deviation 1) |
| Field label / help / error | 13px/600 ink; 11px muted; 11px critical | label same; help and error 13px (deviation 2) |
| Checkbox / radio | native 18px, accent `#147453` | same, inside 44px rows |
| Status pill | 28px, 5/9px padding, 12px/700, pill radius, 6px gap, dot | same |
| Alert | 14px padding, 9px radius, tone-soft background, 16px/700 tone title | same; radius 8px |
| Card / panel | 24px padding, 12px radius, 1px `#DCE4E0`, shadow `0 1px 2px #0f31260d` | same (20px padding below 768px) |
| Card kicker / title | 12px/700 uppercase 0.08em muted on dashboards (10px on operations workspaces); 21px/400 −0.015em | kicker 11px/700, one `--text-eyebrow` token for both (corrected in Phase 5E: this row said "same"); title same |
| Section heading | 30px/400 −0.75px brand-950 (design-system page), 20px/400 (operations) | 28px → 30px at 768px+, regular |
| Welcome band | brand-900, 26/30px padding, 13px radius | same; radius 12px |
| Avatar | 50px circle, brand-100 fill, brand-900 700 initials | sizes 32/40/50 |
| Metric value | 25px/700 brand-950 | same |
| Table header / cell | 10px/700 uppercase muted on canvas, 9/11px padding; 12px cells; selected row brand-50 | 11px header, 13px cells (deviation 3) |
| Timeline step | 24px numbered circle brand-100/action; bordered 9px stage tiles | 28px marker, 8px tiles |

`web/e2e/fidelity.spec.ts` checks these values in a real browser at 1440px.

### 12.2 Inventory

| Group | Component (file) | Variants and states | Reference |
|---|---|---|---|
| A | `Button`, `ButtonLink` (`ui/button.tsx`) | primary, secondary, tertiary, destructive, inverse; md 44px / sm 40px; leading/trailing icon; hover, active, focus, disabled, loading (spinner, `aria-busy`, click blocked); full width; wraps instead of clipping | Buttons |
| A | `IconButton` (`ui/icon-button.tsx`) | 40px, required label, unread indicator | Icon button/Neutral |
| A | `Field` (`ui/field.tsx`) | label, required mark, description, error → `aria-describedby` + `aria-invalid` | Form fields |
| A | `TextInput`, `TextArea`, `SelectField`, `SearchInput` (`ui/inputs.tsx`) | default, error, disabled, read-only, hidden label | Input/Text, Textarea, Select, Input/Search |
| A | `Checkbox`, `RadioGroup`, `Switch` (`ui/inputs.tsx`) | checked, disabled, description; radios in fieldset/legend; switch = `role="switch"` | Checkbox, Radio, Switch |
| A | `Badge` (`ui/badge.tsx`) | success, info, warning, critical, neutral; dot or icon | Status / Badge |
| A | `Alert` (`ui/alert.tsx`) | info, warning, critical (`role="alert"`), success / protected; optional action | Alert/* |
| A | `Card`, `CardHeader`, `Eyebrow`, `SectionHeading` (`ui/card.tsx`) | padding md/sm/none; eyebrow tones muted/action/inverse | Panels |
| A | `MetricCard`, `MetricGrid` (`ui/metric-card.tsx`) | tone edge: success, info, warning, critical, neutral | Metric card |
| A | `Avatar` (`ui/avatar.tsx`) | sm, md, lg | Identity avatars |
| A | `Spinner`, `Skeleton`, `SkeletonCard` | decorative or labelled | Skeleton/Card, loading |
| B | `Tabs` (`ui/tabs.tsx`) | underline; arrows, Home and End; automatic activation | Tabs/Underline |
| B | `Menu` (`ui/menu.tsx`) | menu button; disabled and critical items; align start/end | Menu/Action |
| B | `Dialog`, `ConfirmDialog`, `Drawer` (`ui/dialog.tsx`) | native modal `<dialog>`; Escape, backdrop click, focus return; confirm starts on Cancel; drawer is a right sheet ≥768px and a bottom sheet below | Dialog/Confirmation, Drawer/Detail |
| B | `SearchInput` + `FilterBar` (`ui/filter-bar.tsx`) | search → filters → removable applied filters → live result count | Filter bar, Search and filter pattern |
| B | `DataTable` (`ui/data-table.tsx`) | captioned table ≥768px, stacked labelled rows below; loading skeleton; empty state; selected row | Table/Data |
| B | `Pagination` (`ui/pagination.tsx`) | page window with gaps; compact "Page x of y" below 768px | Pagination/Default |
| B | `SegmentedControl` (`ui/segmented-control.tsx`) | radio group | Segmented control |
| B | `InfoTip` (`ui/tooltip.tsx`) | hover, focus, click; Escape dismiss | contextual help (interpretation) |
| B | `Breadcrumb`, `Stepper` (`ui/navigation.tsx`) | complete / current / upcoming steps | Breadcrumb, booking progress, agent steps |
| B | `ToastRegion`, `useToasts` (`ui/toast.tsx`) | success, dismissible, 6s auto-hide paused on hover/focus | Toast/Success |
| B | `ActivityList`, `ActivityRow`, `AuditEvent`, `DescriptionList`, `RestrictedNotice` (`ui/lists.tsx`) | icon or text marker tiles, link rows with chevron, trailing badge | Activity list, Queue item, Audit event, "Restricted" notes |
| C | `DoctorCard` (`healthcare/doctor-card.tsx`) | verified, next availability or "No open times", fee (information only) | Card/Doctor profile |
| C | `AppointmentCard`, `AppointmentSummary` (`healthcare/appointment.tsx`) | backend status badge, mode, details | Card/Appointment, "Next appointment" |
| C | `MedicalRecordList`, `PatientSummary` (`healthcare/records.tsx`) | record kinds: visit summary, lab result, prescription, consultation | "Recent activity", order/pickup detail |
| C | `AppointmentStatusBadge`, `LabResultStatusBadge`, `ServiceRequestStatusBadge` (`healthcare/status.tsx`) | exactly the backend values; unknown values shown neutrally | Result/Review status, Badge/Appointment |
| C | `StatusTimeline` (`healthcare/status-timeline.tsx`) | vertical or horizontal; complete, current, pending, required, locked; custom state wording | Timeline/Specimen handoff, Processing stages |
| C | `WelcomeBand`, `PermissionBoundary` (`workspace/workspace-sections.tsx`) | — | Welcome band, "Your assigned authority" |
| D | `StateMessage` (`ui/state-message.tsx`) | loading, empty, error (`role="alert"`), success, denied; inline chip or panel | State strip |

Not built, because the reference doesn't support them yet:
- Date and time selectors: they need availability data, so they ship with the booking flow in Phase 4B.
- Document upload: it needs an upload endpoint decision.
- Specimen receipt checklist: it needs a laboratory API.

### 12.3 Deviations and interpretations (Phase 3)

1. **Field value text 16px** (reference 13px semibold): prevents phone zoom on focus and keeps the
   stated 16/24 body size.
2. **Helper and error text 13px** (reference 11px): legibility at the reference's own 200%-zoom criterion.
3. **Table text 11px headers / 13px cells** (reference 10px / 12px): legibility. Below 768px the table
   becomes stacked labelled rows, an interpretation because the reference has no compact table.
4. **Heading weights:** the reference renders card and section titles at 400 weight although the
   type-scale panel says "Semibold". Implemented as rendered; display, H1 and showcase H2 stay semibold.
5. **Radii 9px, 11px and 13px** in the reference are normalised to the approved 8px and 12px tokens.
6. **Status label wording:** for backend states the reference never shows, labels are plain-language
   interpretations: "Reschedule requested", "Declined", "Returned to laboratory", "Open", "In progress",
   "Resolved". `RETURNED` uses the critical tone.
7. **`StateMessage` panel layout, `InfoTip`, `Pagination`, `Breadcrumb` and `Drawer`:** named in the
   inventory but not drawn; built minimally from existing tokens.
8. **Brief mapping:** the brief's "outline" and "ghost" buttons are the reference's *secondary* and
   *tertiary*. No duplicate variants were added. `inverse` is the reference's white button on the
   welcome band.
9. **Focus ring** transitions with button colours (~150ms). It is fully visible on focus; colour settles
   on action green.
10. **Control border `#85928C`** (reference `#AEBBB5`, 1.99:1 on white). Darkened to the lightest
    same-hue value meeting the reference's own 3:1 UI-boundary rule: 3.24:1 on white, 3.03:1 on canvas.
    It also applies to the switch's off track.
11. **Focus ring on dark surfaces:** the action-green ring is 2.46:1 on the sidebar and 1.72:1 on the
    welcome band. Inside those surfaces the ring uses brand-100 `#DCEFE7` (11.8:1 and 8.3:1).
12. **Collapsed rail labels:** below 1200px, icon and number-only navigation shows a visible label
    beside the item on hover and keyboard focus. The reference relies on icons alone.

### 12.5 Accessibility decisions (from the Phase 3 review)

- **Live regions are opt-in for static content.** `Alert` has no live role unless `live="polite"`
  or `live="assertive"` is passed. `StateMessage` announces loading, error and success by default, but
  not empty or denied. Toasts are announced once by the region, not by each toast.
- **Focus is never dropped:**
  - Pagination ends use `aria-disabled`.
  - Disabled menu items remain focusable.
  - Dismissing a toast returns focus to where the user came from.
  - Dialogs return focus to their opener.
  - `ConfirmDialog` focuses Cancel explicitly after opening.
- **Dialogs:**
  - Backdrop dismissal needs press and release on the backdrop.
  - Parent-driven closes don't call `onClose`.
  - `ConfirmDialog` can't be dismissed while `loading`.
- **InfoTip:**
  - Always described.
  - Tap opens it.
  - Escape closes it from anywhere.
  - The bubble is hoverable (WCAG 1.4.13).
- **Selected table rows** carry `aria-current`, a tint and a 3px action edge.
- **Targets:** all interactive targets are at least 40px, except native checkbox and radio inputs.
  Those sit in 44px label rows, so the whole row is clickable.
- **Known limitation:** `cn()` is a plain join, not a class merger, so a consumer `className` can't
  reliably override a conflicting base utility. Add variants instead of overriding.

### 12.4 Usage rules

- Use `Button` for actions and `ButtonLink` for navigation. Always give icon buttons a `label`.
- Wrap every form control in the field components so labels and errors stay linked. Don't use bare `<input>`.
- Status always pairs colour with text (`Badge`, `StateMessage`, `StatusTimeline`); never use colour alone.
- Use the healthcare status badges for API statuses. Don't hand-pick tones per page.
- Presentation-only screens add `<FictionalDataBadge />` (§11).
- `sm:` doesn't exist. Use the compact default, `md:` (768px), `lg:` (1200px) and `xl:` (1440px).

## 13. Account access pages (Phase 4A)

The reference has no sign-in, registration, verification, reset or account screens. These pages are an
interpretation built only from approved tokens and Phase 3 components (`features/auth/auth-shell.tsx`):

1. **Layout:** a slim header (wordmark, "Public website" link), the brand-50 canvas, and two columns at
   1200px and above: an intro (eyebrow, semibold H1, body, the homepage hero's three trust markers) and
   a 30rem white form card. Below 1200px the card stacks under the intro; the trust markers are hidden
   below 768px to keep the form near the top on phones. The footer carries only "MedNet does not replace
   emergency services."
2. **Mode switch:** "Sign in / Create account" uses `SegmentedControl` (a native radio group), replacing
   the legacy pressed-button pair. Forgot password and resend verification are sub-modes reached by text
   buttons, each with a "Back to sign in" route.
3. **Password reset** shows a three-step `Stepper` (Email, Code, New password). The code field accepts
   digits only and shows them spaced with tabular numerals.
4. **Validation** mirrors the backend limits (email ≤254 characters; password 12 characters to 72
   UTF-8 bytes; six-digit code). Problems show under the field, and focus moves to the first one. The
   backend still validates everything.
5. **Messages:** results that appear after an action use `Alert` with a live role (errors assertive,
   confirmations polite). Server wording is shown as returned; the client never reveals whether an
   email has an account.
6. **Account page** shows only email and a readable account type, never clinical data.
   Administrators are forwarded to `/admin`, as before.

## 14. Public website (Phase 4B)

### 14.1 Homepage: measured and matched

| Element | Reference (1440px) | Implemented |
|---|---|---|
| Hero background | `linear-gradient(105deg, #F9FAF8, #F2F6F3 56%, #E7F2ED)` | same |
| Display headline | 72/71px, 600, −0.045em, green-950; 46px on phones, `clamp(50px, 5vw, 72px)` from 768px | same (2026-10-17) |
| Lede | 20/31px, ink-soft, 600px max | same (18px on phones) |
| Search bar | 909px white card, overlay shadow, "Find a doctor" primary button | same width; one field (see 14.2) |
| Photo | 435×500, radius 210/210/22/22, shadow `0 28px 60px rgba(20,70,52,.14)`, bleeds off the right edge | same; centred and narrower below 1200px |
| Floating card | 285×91, 12px radius, 96% white, overlay shadow | same shape (content in 14.2) |
| Section titles | 36/41.4px, 400, −0.9px at every width | same (2026-10-17) |
| Category title | 18/27px, 700 | same |
| Dark band | green-950, 48px padding, 17px titles | same |
| Steps | warm surface `#F6F4EE`, 88px padding, 13px bold green numbers, 22/33px titles | same |

The headline exceeds the 56px `--text-display` token. It follows the rendered reference; the token is
unchanged because it is the stated scale for other display uses.

### 14.2 Content deviations (the reference describes capabilities the backend lacks)

| Reference | Live site | Reason |
|---|---|---|
| "Find verified doctors, speak with them online…" | "Find approved doctors, request appointments in person or by text consultation…" | credential verification unresolved (OQ-09); only in-person and text modes exist |
| Search fields: Location ("Monrovia or online"), Availability ("Any available date") | not shown; one "What care do you need?" field | the directory filters by name/specialty only |
| Trust marker "Verified providers" | "Approved providers" (also on the auth pages) | OQ-09 |
| Floating card "Next available · Today at 2:30 PM · Video consultation" with a live dot | "Open times · Shown before you request · In person or text consultation", no dot | the reference content is fictional; there is no video |
| Four fictional category cards | up to three real specialties with provider counts, plus "Laboratory requests" | no invented services |
| "Representative providers" with fictional profiles, fees, "Profile verified", "View profile" | "Approved providers", real profiles, real next open time, no fee, "See open times" | no fees/payments; no profile page or endpoint |
| Band: verified profiles · suitable location · transparent fixed fees · provider payout tracking | approved profiles · in person or by text · records shared with consent · laboratory and home care | payments out of scope; replacements are implemented capabilities |
| Steps: "Book securely… authorize payment"; "Join online… prescriptions" | "Request a time… the doctor confirms"; "Attend in person or join the text consultation… released results" | no payments or prescribing |
| Header "For providers", "Help"; footer Contact, Help center, Privacy, Accessibility, Terms | linked to placeholder pages stating the content is pending approval (user decision 2026-10-17) | no approved content; nothing is invented (§14.4) |
| Footer "Prototype content is fictional" | removed | live data |

### 14.4 Fidelity correction (2026-10-17)

The homepage, header and footer were re-measured against the reference at 1440, 1024, 768 and 390px
(computed geometry of both pages, same viewport) and corrected. Remaining offsets inside each section are
within 0–3px at all four widths.

| Area | Reference | Was | Now |
|---|---|---|---|
| Content width | 1180px at 1440 (`--container-page` 1244px incl. 32px gutters) | 1176px | 1180px (token updated; applies app-wide) |
| Header | 81px + 1px `line-soft` rule, 95% white; links 15/22.5 semibold, 8px vertical padding, 28px apart, 42px after the wordmark; links from 1200px; Sign in a 44px text button 10px before Book; 69px on phones with a 13px Book button | 72px, 14px links 24px apart, links from 768px (they overflow there with four links) | as the reference |
| Hero | from 1200px a 650px band, text column (70px padding) and photo centred vertically; 78/100px top padding on phones/tablets; photo 310×390 on phones, 435×500 above, flush with the band's bottom edge on tablets; eyebrow 12/18 with 0.11em tracking and a 23px rule; card on the photo's bottom edge, 43px icon tile, 12/15/12px lines | top-weighted padding (704px tall), 11px eyebrow, 44/56px headline | as the reference |
| Section eyebrows | 12px, 0.11em, in a 24px line box | 11px, 0.08em | `Eyebrow size="section"` (workspace eyebrows unchanged) |
| Category cards | 22px padding, gaps 14/6px, 14/19.6 description, muted 18px chevron 29px below | 23px padding, 20/4px gaps, ink chevron | as the reference (icon tile keeps the approved 8px radius; the reference uses 10px) |
| Dark band | 4 equal columns 32px apart, 42px tiles on white/8% with `#8BD9BA` icons, 13/19.5 text in `#BDD0C8` | 44px gaps, 44px tiles, 14px `green-100` text | as the reference (new tokens `green-300`, `ink-inverse-soft`) |
| Steps | 88px padding at every width, 24px under a `#D6D2C8` rule, 28px number-to-title, 16/25.6 text max 320px | 64px below 1200px, 32px, 36px | as the reference (token `line-warm`) |
| Footer | 55px top padding; 1.5fr + 3×1fr with 55px gaps (1.4fr + 2×1fr with Support under the brand on tablets; brand row then 2 columns 30px apart on phones); bold 13px headings 15px above links 9px apart; divider inside the content width; 12px bottom row, stacked on phones | 48px, 1.4fr grid, 40px gaps, semibold headings, divider across the gutters | as the reference |

Placeholder pages (`/for-providers`, `/help`, `/contact`, `/privacy`, `/accessibility`, `/terms`) use
`PageIntro` and say only that the content is being prepared and not yet approved, with links to Find
care and How it works. They are `noindex`. They must be replaced, not extended, once content is approved.

Not changed, by earlier decision: the single search field, content in §14.2, the mobile menu toggle, and
44px minimum targets on "View all services" (the reference's link is 38px tall).

### 14.3 Interpreted pages

- **`/find-care`:** `PageIntro`; a search form (GET, with the specialty kept as a hidden field); removable
  filter chips; a results count in a polite live region; a 1/2/3-column `DoctorCard` grid with
  `Pagination`. Each card opens a `Drawer` (right sheet from 768px, bottom sheet below) with a radio
  list of open times grouped by day in Liberia time, the selected-time summary, and a footer action that
  depends on the session (sign in / send request / patient-only notice).
- **About and Services:** `PageIntro` (the homepage gradient, eyebrow and semibold H1 at 48px), then the
  homepage steps pattern (About) or a 1/2/3-column icon `Card` grid (Services) with `#id` anchors and an
  informational `Alert` listing what is not available.
- Times are always labelled "Liberia time (GMT)", so people booking from abroad are not misled.

## 15. Patient workspace (Phase 4C)

### 15.1 Layout

- `app/patient/layout.tsx` renders `PatientWorkspace` (`features/patient/patient-workspace.tsx`): a
  UX-only session guard plus `WorkspaceShell`. Signed out → "Sign in to continue" with
  `?redirect=` set to the current path and query. Another role → `homePathForRole`. Session check
  failure → error with retry.
- Top bar: the overview shows the reference's date eyebrow and "Good morning, {first name}"
  (Liberia time; neutral greeting without a profile). Other views show "Patient workspace" and the
  view name. The reference's search and messages icon buttons are replaced by an **Account** menu
  (Your profile, Sign out). There is no global search API, and sign-out had no other home. The menu
  is icon-only below 768px so the page title is not truncated.
- **Overview:** the reference's band, Next appointment, For you, Recent activity, Payments and
  protected note. Below 1200px the cards stack in reading priority (`display: contents` + `order`).
  From 1200px there are two independent columns, which avoids the reference's row gaps once real
  content varies in height. Two cards are added: **Requests** (laboratory and home care) and
  **Notifications** (with "Mark as read").
- **Sub-views** use the reference template (`view-frame.tsx`): a `ViewBand` with eyebrow, title,
  description and one action; the content column; and the "Privacy and access" card. The
  reference's "View access history" link is omitted because there is no patient access-history
  endpoint. Appointments and Medical information use `Tabs`, mounting only the active panel so each
  tab loads fresh data.

### 15.2 Content deviations

| Reference | Live | Reason |
|---|---|---|
| Next appointment: "Video consultation", camera icon, "Fixed cost USD 35 authorized", "Prepare for visit" | consultation mode with a matching icon (text/in person), "Time zone: Liberia time (GMT)", "View details" | no video, fees or visit-preparation feature |
| Doctor specialty under the name | "Approved provider" | `AppointmentDetails` has no specialty |
| For you: "Confirm virtual care consent", "Reply to your doctor" | derived only from real state: incomplete profile; proposed new time | no virtual-care consent or unread-message API |
| "Prescription available · Private" etc. | real records (category, date) and released lab results | — |
| Payments "Latest receipt USD 35.00 Authorized" | kept, labelled **Fictional data**, with "MedNet does not take payments" | approved presentation-only rule |
| Template list items (APT-3084 …) with "View appointment" | appointment rows with date tile, status, mode, duration; Cancel; proposed-time panel with Accept / Keep original / Cancel | real actions only |
| Messages: "MedNet Support", "Laboratory updates" threads | doctor conversations only; "Start a conversation" lists the care team | no support or lab messaging |
| Help: "Get help", "Contact support" | three factual topics linking to the real feature; the support team is stated as not available | no support channel or approved help content |

### 15.3 Added in the second pass

- **Appointment details:** a `Drawer` (bottom sheet below 768px) with a `DescriptionList` and a status
  explanation; its footer offers "Text consultation" and "Cancel appointment" where allowed.
- **Text consultation:** the sub-view template with appointment context, a "Join" card, then a
  message thread in the Messages style. Messages are labelled "You" and "Doctor".
- **Session ended:** the access-state layout (wordmark header and centred `StateMessage`) with no
  patient data and a "Sign in again" button.
- **Specialty labels** (public site and directory): spelling equivalents are shown under one label;
  see `FRONTEND_PROGRESS.md` (4C, brief reconciliation).

Medications and vitals are labelled as the patient's own notes, shown as entered with no ranges.
Request forms and messages carry the approved line "MedNet does not replace emergency services."

## 16. Doctor workspace (Phase 4D)

### 16.1 Layout

- `app/provider/layout.tsx` → `ProviderWorkspace`: the same `WorkspaceShell`, Account menu and access
  states as the patient workspace (`features/workspace/access-state.tsx`). Top bar: date eyebrow and
  "Good morning, Dr. {surname}" on the overview.
- **Overview** follows the reference: a dark "Today's practice" band; four icon stat tiles (icon tile
  plus value, label and hint, as drawn; not the operations `MetricCard` edge style); then two
  independent columns (today's schedule, requests, notifications | clinical attention, clinical
  tools); then the full-width earnings card with four tiles; then the separation note. Below 1200px
  the cards stack in reading priority.
- Sub-views use the patient sub-view template (`ViewBand` with eyebrow "Doctor workspace", content
  column, "Privacy and access" card). Appointments and patient detail use `Tabs`, mounting only the
  active panel.
- Applicants (PATIENT role with an application) get a standalone status page without the workspace
  shell.

### 16.2 Content deviations

| Reference | Live | Reason |
|---|---|---|
| "Consultation status: Available online" toggle | "Open times: N published" link to Schedule | no presence/availability-status API |
| Tiles: Results to review, Notes to complete, Clinical messages | Appointments today, Requests to answer, Results to review, Unread notifications | no note drafts; no unread-message count API |
| Schedule rows "Follow-up · Video", "Asynchronous", "Needs reply" | patient name, consultation mode (text or in person), backend status badge | no video; real statuses only |
| Clinical attention: lab result, encounter note, clinical message, with ages | pending lab results (care patients with consent) and patient-requested new times | real items only |
| Appointment request "New patient request", patient details limited | requests show the patient's name (the API returns it) and the same limitation note; records stay closed until confirmed and consented | API contract |
| Clinical tools: Document consultation, Create prescription, Create lab order | Document consultation, Review results, Publish open times; a note that prescribing and lab orders are not available | no prescribing or ordering API (Services page lists electronic prescribing as unavailable) |
| Earnings: balance, settlement, payout method "Verified · Account ending 4821" | labelled **Fictional data**; "No payouts on MedNet", "No payment details are collected" | payments out of scope; never "Verified" |
| Patients list "PT-10482 · Authorized care relationship" | patient name with "Care relationship" or "Request only" | the API returns names, not patient references |
| Messages: Clinical inbox, Laboratory partner, Administration | patient conversations only | no partner or admin messaging |

## 17. Administrative workspaces (Phase 4E)

### 17.1 Layout

- `app/admin/layout.tsx` → `AdminWorkspace`: `WorkspaceShell` with numbered navigation; the session role
  picks the `super-admin` or `admin` menu (`features/workspace/workspaces.ts`). Top bar: "Current
  workspace" eyebrow and the workspace name, as drawn; the Account menu replaces the search,
  notification and "Fictional data" controls.
- **Page heading** (`AdminPageHeading`): green role eyebrow, 32px title, description, optional action at
  the right — drawn on the canvas, not in a dark band (matches the operations reference, unlike the
  patient and doctor bands).
- **Overviews** follow the reference: four edge-toned `MetricCard`s, then two cards side by side from
  1200px (governance queue + recent events; priority work + permission boundary), then (super
  administrator) the six "Organizations and access" tiles and the high-impact area.
- **Sub-views** use the reference's list + "Selected record" template (`RecordLayout`, 22rem panel,
  sticky from 1200px, stacked below the list on smaller screens). Choosing a record moves focus to the
  panel heading. Lists use `SelectableRow` (`aria-pressed`) or `DataTable` (cards below 768px).
- High-impact actions (suspend, mark unverified, delete, provider decline/suspend, request cancel) use
  `ConfirmDialog` with the destructive tone; focus starts on Cancel.

### 17.2 Content deviations

| Reference | Live | Reason |
|---|---|---|
| "98.7% Service activity" | API status (Up) with check time | no service-activity metric; health endpoint exists |
| "4 Security events, 2 require review" | open requests (home care · laboratory) | no security-event API |
| Governance queue: doctor credential review "Verify", laboratory re-review, elevated access request | pending provider applications ("Awaiting review") and the unverified-email count | no credential verification, organisation or access-request API; never "Verify" |
| Recent access events (role assignment, break-glass, key rotation) | latest administrative audit events | real audit trail only |
| Tiles: Doctors, Laboratories, Collection providers, Platform settings, Integrations | Doctors, Laboratory staff, Home-care staff (account filters), Platform status, Task queue | no organisation, settings or integration API; collection providers have no role |
| "Suspend selected account" button on the overview | "Manage accounts" link; suspension happens on a selected account with confirmation | the overview has no selected account |
| "Configuration history" | "Audit trail" | no configuration history |
| Administrator metrics "Tasks within SLA 94%" | API status | no SLA data |
| Administrator "Approval required" group | "Super administrator only" | there is no approval workflow; those actions are role-restricted |
| Operational signals cards | omitted; counts appear in metrics and priority work | no signal data |
| Sub-view template "Open workflow / View activity", "Status: all / Date: current period" | real actions for the record; real filters only (no date filter in the API) | API contract |
| Super administrator menu has six items | eight: Task queue and Appointments added | the backend gives `SUPER_ADMIN` every `ADMIN` capability; keeps legacy queues reachable |
| Escalations | presentation-only list with the Fictional data badge | no escalation API |
| Verification (credentials) | email verification queue | MedNet does not verify credentials |

## 18. Laboratory workspace (Phase 4F)

### 18.1 Layout

- `app/laboratory/layout.tsx` → `LaboratoryWorkspace`: `WorkspaceShell` with the numbered laboratory
  menu (seven items, as drawn), "Current workspace · Laboratory" top bar and the Account menu.
- Operations building blocks now live in `features/workspace/record-layout.tsx` (`OperationsPageHeading`,
  `RecordLayout`, `SelectedRecord`, `ListCard`, `SelectableRow`) and serve both the administrative and
  laboratory workspaces.
- **Overview** follows the reference: page heading with a secondary action, four edge-toned metric cards,
  the order queue (`DataTable`, cards below 768px) beside the order detail panel, then the processing
  stages (`StatusTimeline` horizontal) with the reference's warning note.
- **Order detail**: reference, status badge, requested-test panel (the reference's green "Requested test"
  block), dates, a vertical request timeline, the action for the current state, and the "Restricted" note.
  Submitting a result and starting an order both confirm first.

### 18.2 Content deviations

| Reference | Live | Reason |
|---|---|---|
| Metrics "New orders · 4 require review", "Accepted · 9 awaiting collection", "In progress · 3 due today", "Completed · last 7 days" | Not started, Processing, Released, Cancelled (counts of assigned orders) | no acceptance, collection or due-date data |
| Queue columns Patient PT-…, Requesting doctor, Turnaround; filters Test and Urgency | Order (LAB-reference from the request ID), Requested test, Status, Received; filters search and status | the API returns no patient reference, doctor, urgency or turnaround |
| Statuses "Awaiting acceptance", "Awaiting specimen", "Specimen rejected" | Not started, Processing, Result released, Cancelled | backend statuses only |
| Order detail: specimen requirements, laboratory fee, estimated turnaround, authorized context, "Accept order" / "Reject with reason" | requested test text, dates, timeline; "Start processing" | no such data; no accept/reject endpoint; payments out of scope |
| Processing stages 1–7 (order accepted … professional validation … released to doctor) | Assigned → Processing → Result submitted → Doctor review → Released to patient | real MedNet flow; the doctor releases to the patient |
| "Upload does not equal validation" | "Submitting does not release a result" | same principle for the real flow |
| Specimen receipt checklist, Accept/Reject specimen, Upload result document, Validate result, Release to doctor | text result form ("Submit for doctor review"); specimen items moved to the presentation-only Specimens page with disabled checkboxes | no specimen or document API; release is the doctor's |
| Provider finance block on the overview | Pricing & payouts page, presentation-only | payments out of scope |
| "Test catalogue" heading action | "All orders" | no catalogue |

## 19. Preview operations workspaces (Phase 4G)

### 19.1 Layout and components

- `app/(preview)/preview/*` → `PreviewWorkspace`: the shared `WorkspaceShell` (numbered menu, "Current
  workspace" top bar with the Fictional data badge) plus a sticky **"Preview only — Fictional data"**
  banner in the new `banner` slot. No Account menu: there is no session.
- `PreviewAction`: every control the reference draws. Focusable, `aria-disabled` (the design-system
  disabled look), announces "Preview only: no action was taken." when activated.
- `PreviewSubView`: the reference's generic sub-view (submenu list with search + "Selected record" with
  actions and "Restricted" note), built on the shared `record-layout` blocks.
- Overviews follow each reference: collection provider (metrics, dispatch queue + pickup detail, agent
  workload bars, exceptions, payouts), collection agent (rules band + phone frame with a five-step list),
  support (metrics, ticket list, conversation, disabled reply), finance (metrics, transaction table +
  detail, recipients, audit events), specimen lifecycle (ten stage cards, exception cards, dependencies),
  permissions matrix (dark-header table, principle cards).

### 19.2 Deviations

| Reference | Preview | Reason |
|---|---|---|
| "Switch workspace" selector and "Prototype role" footer | `/preview` index and an "All previews" link in the banner | no fictional role switching in a real product |
| Primary actions (Assign pickup, Send response, Review full refund, Export report…) | `PreviewAction`, no effect | no backend; must not mutate anything |
| Reply box | disabled | no messaging for these roles |
| Collection agent step indicator with labels under markers | simple five-column step list | the shared `Stepper` truncates labels inside the narrow phone frame |
| Specimen lifecycle stages | each stage also marked "In MedNet" / "Partly in MedNet" / "Not in MedNet" with a note | the preview must not imply clinical workflows MedNet lacks |
| Permissions matrix | marked "design proposal", followed by a factual "What MedNet enforces today" list | the matrix includes roles that do not exist |
| Payout method "Verified · Account ending 4821" | "Not collected" | MedNet collects no payment details; never "Verified" |
| Matrix table at 390px | scrolls sideways inside a labelled, focusable region | six columns of prose cannot stack legibly |

### 19.3 Laboratory results (4G addition to §18)

Order badges and timelines now come from the server's result status: "Awaiting doctor review",
"Returned · correction needed" (critical tone; the timeline step is "required"), "Result released". The
Results page groups the laboratory's submissions into Returned, Awaiting doctor review and Released.

## 20. Home-care workspace (Phase 5C, no reference design)

The reference has no home-care workspace. It is an interpretation built only from existing tokens,
components and the laboratory workspace layout (§18); no new component or token was added.

| Element | Interpretation |
|---|---|
| Frame | `WorkspaceShell` with a numbered menu like the laboratory's: Overview, Visits, Help and guidance; "Current workspace · Home care"; Account menu (Your account, Sign out) |
| Overview | Page heading with an "All visits" action; four edge-toned metric cards (Need a visit time, Scheduled, In progress, Completed); the visit queue limited to five ("Next visits"); "Visit stages" horizontal `StatusTimeline` with an info note on what MedNet does not record |
| Visits | `RecordLayout`: `ListCard` with `DataTable` (cards below 768px), search and status filter; `SelectedRecord` with two green panels (care requested, visit location, as written by the patient), dates, a vertical timeline, the action for the current state and the "Restricted" note |
| Actions | Schedule (a `datetime-local` field in Liberia time, as on the doctor's record form), Start visit, Mark visit completed; each confirms first. Stale or refused actions show a page-level critical alert and refresh the queue |
| Reference | `HCR-` + the first 8 characters of the request ID, which administrators see as "Request XXXXXXXX"; `HC-` is already the home-care account public-ID prefix |
| Help | Factual questions and answers (`dl` in a card) and the existing "MedNet does not replace emergency services" alert |
| Not drawn | No presentation-only pages: the workspace shows only what the API supports |

## 21. Stylesheet cleanup (Phase 5D)

Removed from `web/src/app/globals.css` (139 lines remain, from 189), each confirmed unused by searching
every `className`, `cn()`/template string and stylesheet in `web/src` for the class name, and by checking
that no selector is built dynamically:

- Legacy variables `--ink`, `--body`, `--muted`, `--paper`, `--white`, `--line`, `--green`, `--green-dark`,
  `--coral`, `--lime`, `--font-display` (0 references outside the stylesheet).
- Legacy classes `.wordmark`, `.hero`, `.eyebrow`, `.button`, `.text-link`, `.approach-link`,
  `.section-heading`, `.closing-section`, `.approach-copy`, `.decision-note`, `.inner-page-heading`, their
  900px and 640px media rules, and a duplicate reduced-motion rule (the base layer keeps the complete one).
  Similar-looking matches in the code are Tailwind utilities (`text-eyebrow`, `text-button`), not these
  classes.

Kept: tokens, the base layer (focus ring, reduced motion, body), and the three element rules above, which
still apply site-wide; the link focus outline now names the token (`--color-green-700`) instead of the
removed `--coral` alias (same value).

**Visual verification.** Before and after the change, every element's computed style (36 properties:
colour, background, type, spacing, borders, outline, layout, transform, transition) was recorded on 18
pages covering public, sign-in, patient, doctor, administrator, laboratory, home-care and preview screens,
at 390, 820 and 1440px, with the API stubbed. Two unchanged runs matched exactly (0 of 54 differ), and the
before/after comparison also matched exactly (**0 of 54 differ**). Full-page screenshots were taken as
well: 30 of 54 were byte-identical; the other 24 also differed between the two unchanged runs (stub dates
are relative to the current time), so none of the differences comes from the stylesheet.

New pagination controls (Phase 5D) reuse `Pagination` (§12); no new component or token was added.

## 22. Final fidelity audit (Phase 5E)

**Method.** Every designed reference screen (homepage, patient and doctor dashboards, and all nine operations
workspaces) was compared with the implementation in two ways: side-by-side 1440px screenshots (390px for the
dashboards and the mobile-first collection agent), and an automated comparison of the computed font size and
weight of every text element whose wording appears on both (live reference vs. stubbed implementation).
Differences explained by the tokens below were set aside; each remaining one was fixed or classified.

**Explained by approved tokens (not defects).** Reference text at 9–12px is raised to the 11px eyebrow and
13px caption minimums (§12.3 deviations 2–3, readability); 12–14px/700 values use the 14px/600 Data token;
field values are 16px (deviation 1); button labels use the 15px/700 button token, also where the reference
draws an 11–12px text link ("Report delay…", "Escalate to…"); the workspace top-bar title is the page H1 at
22px/600 (§12.3 item 4: H1 stays semibold; the reference shows 23px/400 on dashboards and 16px/700 on
operations workspaces).

**Fixed (unintended, Low severity).**

| Element | Reference | Was | Now |
|---|---|---|---|
| List and activity row titles (`ActivityRow`, schedule, records, messages, patient lists) | 16px/700 | 16px/600 | 16px/700 |
| Audit event titles (`AuditEvent`, laboratory example audit) | 16px/700 | 14px/600 | 16px/700 |
| Super-administrator management tiles, "High-impact actions" | 16px/700 | 16px/600 | 16px/700 |
| Preview titles: agent names, collection exceptions, "Authorized instruction", "Refund reason", "Provisional allocation", recipient groups, next-pickup destination | 16px/700 | 16px/600 | 16px/700 |
| Payout summary values (doctor, laboratory, preview partners) | 17px/700 | 21px or 16px | 17px/700 |
| Specimen lifecycle stage and exception titles | 15px/400 and 14px/400 | 16px/600 | as reference |
| Access-principle titles (permissions matrix) | 15px/400 | 21px | 15px/400 |
| Collection agent: "Next pickup", place, "Confirm arrival" | 16/700, 19/400, 18/400 | 14/700, 21/400, 21/400 | as reference |
| Public header "Sign in" and "Book an appointment" | 15px/700 | 14px/600 | 15px/700 (button token) |
| Wordmark | 25px | 22px | 25px |
| Desktop table cells | vertically centred | top-aligned (row text sat above the centred badges and buttons) | centred |

`e2e/fidelity.spec.ts` now also asserts the row-title, audit-title and table-alignment values. The other
fixes are covered by the comparison script (session scratchpad, not committed), re-run after the changes:
the only remaining differences are text matched in a different context, the documented 16px field value
and the 15px button token.

**Documentation correction.** §12.1 said the card kicker matched the reference's 12px; it is 11px, one token
covering the reference's 12px (dashboards) and 10px (operations).
