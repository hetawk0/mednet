import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { stubAdminApi } from "./support/admin-stub";
import { stubHomeCareApi } from "./support/home-care-stub";
import { stubLaboratoryApi } from "./support/laboratory-stub";
import { stubPatientApi } from "./support/patient-stub";
import { stubProviderApi } from "./support/provider-stub";
import { inspect, keyboardWalk, settle } from "./support/quality";

/**
 * Phase 5E cross-route quality sweep (mocked API). The workspace specs check each area's own behaviour;
 * this one applies the same page-level rules to every route at 390/820/1440 so a regression anywhere
 * (a dead link, a control with no focus ring, a clipped label, a missing landmark) is caught:
 *
 * - one `main`, one `h1`, a page title and `lang`;
 * - no horizontal scroll and no clipped text;
 * - every internal link resolves to an app route;
 * - every visible control is at least 24×24 CSS px (WCAG 2.2 SC 2.5.8; inline text links excepted);
 * - a keyboard walk reaches every control with a visible focus indicator and never lands on hidden content;
 * - WCAG 2.2 A/AA rules from axe.
 */

/** App routes (from `src/app`), dynamic segments as patterns. Anything else linked from a page is a dead link. */
const ROUTES: RegExp[] = [
  /^\/$/,
  /^\/(find-care|about|services|sign-in|reset|verify|account|for-providers|help|contact|privacy|accessibility|terms)$/,
  /^\/patient(\/(find-a-doctor|appointments|appointments\/consultation|messages|records|payments|help|profile))?$/,
  /^\/provider(\/(schedule|appointments|appointments\/consultation|patients|patients\/[^/]+|clinical-work|messages|earnings|help|profile))?$/,
  /^\/admin(\/(users|providers|verification|security|configuration|tasks|appointments|onboarding|escalations|reports))?$/,
  /^\/laboratory(\/(orders|specimens|results|test-catalogue|pricing-payouts|audit-history|profile))?$/,
  /^\/home-care(\/(visits|help|profile))?$/,
  /^\/preview(\/(collection|collection-agent|support|finance)(\/[a-z-]+)?)?$/,
  /^\/preview\/specimen-lifecycle(\/(exception-paths|dependencies))?$/,
  /^\/preview\/permissions-matrix(\/(access-principles|audit-requirements))?$/,
  // Full-page navigation to the API's OAuth start (Google sign-in), not an app route.
  /^\/api\/v1\/auth\/oauth2\/authorization\/google$/,
];

type Area = { name: string; stub: (page: Page) => Promise<unknown>; paths: string[] };

const json = (body: unknown, status = 200) => ({ status, contentType: "application/json", body: JSON.stringify(body) });
const unauthorized = json({ success: false, error: { code: "UNAUTHORIZED", message: "Sign-in required", details: [] } }, 401);

/** Signed-out visitor: public directory data, no session. */
async function stubPublic(page: Page) {
  const at = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString();
  const providers = [
    { id: "p1", displayName: "Dr. Ama Kollie", specialty: "Family medicine", credentialReference: "x", nextAvailableAt: at(26) },
    { id: "p2", displayName: "Dr. Joseph Tarr", specialty: "Pediatrics", credentialReference: "x", nextAvailableAt: null },
  ];
  const paged = (content: unknown[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 25 });
  await page.route("**/api/v1/**", (route) => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (path === "/providers") return route.fulfill(json(paged(providers)));
    if (path === "/providers/specialties") return route.fulfill(json([{ specialty: "Family medicine", providers: 1 }, { specialty: "Pediatrics", providers: 1 }]));
    if (/^\/providers\/p\d\/availability$/.test(path)) return route.fulfill(json(paged([{ id: "s1", startsAt: at(26), endsAt: at(26.5), consultationMode: "TEXT" }])));
    if (/^\/providers\/p\d$/.test(path)) return route.fulfill(json(providers.find((p) => path.endsWith(p.id))));
    if (path === "/auth/google/status") return route.fulfill(json({ enabled: true }));
    if (path === "/auth/csrf") return route.fulfill(json({ headerName: "X-XSRF-TOKEN", token: "test-token" }));
    return route.fulfill(unauthorized);
  });
}

const AREAS: Area[] = [
  { name: "public", stub: stubPublic, paths: ["/", "/find-care", "/find-care?provider=p1", "/about", "/services", "/for-providers", "/privacy", "/no-such-page"] },
  { name: "account access", stub: stubPublic, paths: ["/sign-in", "/reset", "/verify", "/account"] },
  {
    name: "patient",
    stub: (page) => stubPatientApi(page),
    paths: [
      "/patient",
      "/patient/find-a-doctor",
      "/patient/appointments",
      "/patient/appointments?tab=home-care",
      "/patient/appointments/consultation?appointment=apt-next",
      "/patient/messages?conversation=conv-1",
      "/patient/records",
      "/patient/records?tab=laboratory",
      "/patient/records?tab=sharing",
      "/patient/payments",
      "/patient/help",
      "/patient/profile",
    ],
  },
  {
    name: "doctor",
    stub: (page) => stubProviderApi(page),
    paths: [
      "/provider",
      "/provider/schedule",
      "/provider/appointments",
      "/provider/appointments/consultation?appointment=apt-today",
      "/provider/patients",
      "/provider/patients/pat-james",
      "/provider/patients/pat-james?tab=results",
      "/provider/clinical-work",
      "/provider/messages?conversation=conv-1",
      "/provider/earnings",
      "/provider/help",
      "/provider/profile",
    ],
  },
  {
    name: "super administrator",
    stub: (page) => stubAdminApi(page, { role: "SUPER_ADMIN" }),
    paths: ["/admin", "/admin/users", "/admin/providers", "/admin/verification", "/admin/security", "/admin/configuration", "/admin/tasks", "/admin/appointments"],
  },
  {
    name: "administrator",
    stub: (page) => stubAdminApi(page, { role: "ADMIN" }),
    paths: ["/admin", "/admin/onboarding", "/admin/onboarding?tab=accounts", "/admin/escalations", "/admin/reports"],
  },
  {
    name: "laboratory",
    stub: (page) => stubLaboratoryApi(page),
    paths: ["/laboratory", "/laboratory/orders", "/laboratory/results", "/laboratory/specimens", "/laboratory/test-catalogue", "/laboratory/pricing-payouts", "/laboratory/audit-history", "/laboratory/profile"],
  },
  { name: "home care", stub: (page) => stubHomeCareApi(page), paths: ["/home-care", "/home-care/visits", "/home-care/help", "/home-care/profile"] },
  {
    name: "previews",
    stub: async () => {},
    paths: [
      "/preview",
      "/preview/collection",
      "/preview/collection/pickup-requests",
      "/preview/collection-agent",
      "/preview/collection-agent/offline-queue",
      "/preview/support",
      "/preview/support/tickets",
      "/preview/finance",
      "/preview/finance/refunds",
      "/preview/specimen-lifecycle",
      "/preview/specimen-lifecycle/exception-paths",
      "/preview/specimen-lifecycle/dependencies",
      "/preview/permissions-matrix",
      "/preview/permissions-matrix/access-principles",
      "/preview/permissions-matrix/audit-requirements",
    ],
  },
];

for (const area of AREAS) {
  test.describe(`quality sweep: ${area.name}`, () => {
    for (const path of area.paths) {
      test(path, async ({ page }) => {
        await area.stub(page);
        await page.goto(path);
        await settle(page);

        const found = await page.evaluate(inspect);
        expect.soft(found.mains, "exactly one visible main landmark").toBe(1);
        expect.soft(found.h1s, "exactly one h1").toBe(1);
        expect.soft(found.title.trim(), "page title").not.toBe("");
        expect.soft(found.lang, "document language").toBe("en");
        expect.soft(found.overflow, "no horizontal scroll").toBeLessThanOrEqual(0);
        expect.soft(found.clipped, "no clipped text").toEqual([]);
        expect.soft(found.small, "targets of at least 24×24 px").toEqual([]);
        const dead = [...new Set(found.links)].filter((href) => !ROUTES.some((route) => route.test(href.split(/[?#]/)[0])));
        expect.soft(dead, "internal links resolve to app routes").toEqual([]);

        const walk = await keyboardWalk(page);
        expect.soft(walk.stops, "keyboard reaches controls").toBeGreaterThan(0);
        expect.soft(walk.problems, "keyboard walk").toEqual([]);

        const axe = await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
          .exclude("nextjs-portal")
          .analyze();
        expect.soft(axe.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes[0]?.target.join(" ")}`), "axe WCAG 2.2 AA").toEqual([]);
      });
    }
  });
}

/**
 * Empty and failed-load states per workspace. Wrong-role redirects and expired sessions are covered in
 * each workspace spec; an unreachable API was only covered for the patient workspace before Phase 5E.
 */
const STATE_PAGES: Array<{ name: string; path: string; stub: (page: Page, empty: boolean) => Promise<unknown> }> = [
  { name: "patient", path: "/patient/appointments", stub: (page, empty) => stubPatientApi(page, { empty }) },
  { name: "doctor", path: "/provider/appointments", stub: (page, empty) => stubProviderApi(page, { empty }) },
  { name: "super administrator", path: "/admin/providers", stub: (page, empty) => stubAdminApi(page, { role: "SUPER_ADMIN", empty }) },
  { name: "administrator", path: "/admin/tasks", stub: (page, empty) => stubAdminApi(page, { role: "ADMIN", empty }) },
  { name: "laboratory", path: "/laboratory/orders", stub: (page, empty) => stubLaboratoryApi(page, { empty }) },
  { name: "home care", path: "/home-care/visits", stub: (page, empty) => stubHomeCareApi(page, { empty }) },
];

async function expectSoundLayout(page: Page) {
  const found = await page.evaluate(inspect);
  expect.soft(found.overflow, "no horizontal scroll").toBeLessThanOrEqual(0);
  expect.soft(found.clipped, "no clipped text").toEqual([]);
  expect.soft(found.small, "targets of at least 24×24 px").toEqual([]);
  const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).exclude("nextjs-portal").analyze();
  expect.soft(axe.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes[0]?.target.join(" ")}`), "axe WCAG 2.2 AA").toEqual([]);
}

test.describe("quality sweep: states", () => {
  for (const { name, path, stub } of STATE_PAGES) {
    test(`${name}: an empty account shows an empty state`, async ({ page }) => {
      await stub(page, true);
      await page.goto(path);
      await settle(page);
      await expect(page.getByRole("main").getByText(/^(No |Nothing |You have no |There are no )/).first()).toBeVisible();
      await expectSoundLayout(page);
    });

    test(`${name}: an unreachable API shows an error with retry and no stale data`, async ({ page }) => {
      await stub(page, false);
      // Registered last, so it wins over the stub for everything except the session check.
      await page.route("**/api/v1/**", (route) =>
        new URL(route.request().url()).pathname.startsWith("/api/v1/auth/") ? route.fallback() : route.fulfill({ status: 502, body: "" }),
      );
      await page.goto(path);
      await expect(page.getByRole("alert").first()).toBeVisible();
      await expect(page.getByRole("main").getByRole("button", { name: /try again|retry/i }).first()).toBeVisible();
      await expect(page.getByRole("main").getByRole("table")).toHaveCount(0);
      await expectSoundLayout(page);
    });
  }
});

/**
 * Reflow (WCAG 2.2 SC 1.4.10): at 320 CSS px, the width of a 1280px window at 400% zoom, content must
 * still fit without two-dimensional scrolling or clipped text. Runs once, from the compact project.
 */
test.describe("quality sweep: 320px reflow", () => {
  test.skip(({ viewport }) => viewport?.width !== 390, "runs once, from the compact project");
  for (const area of AREAS) {
    test(`${area.name} reflows at 320px`, async ({ page }) => {
      test.setTimeout(120_000);
      await page.setViewportSize({ width: 320, height: 640 });
      await area.stub(page);
      for (const path of area.paths) {
        await page.goto(path);
        await settle(page);
        const found = await page.evaluate(inspect);
        expect.soft(found.overflow, `${path}: no horizontal scroll`).toBeLessThanOrEqual(0);
        expect.soft(found.clipped, `${path}: no clipped text`).toEqual([]);
      }
    });
  }
});
