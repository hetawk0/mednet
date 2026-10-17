import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

/**
 * Public pages with the API stubbed in the browser (layout, accessibility, links, keyboard).
 * Live behaviour against the real API is covered by find-care.live.spec.ts.
 */
const inHours = (h: number) => new Date(Date.now() + h * 3_600_000).toISOString();
const providers = [
  { id: "p1", displayName: "Dr. Ama Kollie", specialty: "Family medicine", credentialReference: "x", nextAvailableAt: inHours(26) },
  { id: "p2", displayName: "Dr. Joseph Tarr", specialty: "Pediatrics", credentialReference: "x", nextAvailableAt: null },
  { id: "p3", displayName: "Dr. Musu Kamara", specialty: "Family medicine", credentialReference: "x", nextAvailableAt: inHours(50) },
];
/** `GET /providers/specialties` for the providers above (Phase 5D). */
const specialties = [
  { specialty: "Family medicine", providers: 2 },
  { specialty: "Pediatrics", providers: 1 },
];
const slots = [
  { id: "s1", startsAt: inHours(26), endsAt: inHours(26.5), consultationMode: "IN_PERSON" },
  { id: "s2", startsAt: inHours(50), endsAt: inHours(50.5), consultationMode: "TEXT" },
];
const page = (content: unknown[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 25 });

async function stubApi(p: Page, { down = false } = {}) {
  await p.route("**/api/v1/providers?**", (route) =>
    down ? route.fulfill({ status: 502, body: "" }) : route.fulfill({ json: page(providers) }),
  );
  await p.route("**/api/v1/providers/*/availability**", (route) => route.fulfill({ json: page(slots) }));
  await p.route("**/api/v1/providers/specialties", (route) => (down ? route.fulfill({ status: 502, body: "" }) : route.fulfill({ json: specialties })));
  await p.route(/\/api\/v1\/providers\/p\d$/, (route) => {
    const found = providers.find((provider) => route.request().url().endsWith(`/${provider.id}`));
    return found ? route.fulfill({ json: found }) : route.fulfill({ status: 404, json: { success: false, error: { code: "NOT_FOUND", message: "Provider not found", details: [] } } });
  });
  await p.route("**/api/v1/auth/session", (route) =>
    route.fulfill({ status: 401, json: { success: false, error: { code: "UNAUTHORIZED", message: "x" } } }),
  );
}

async function expectNoHorizontalScroll(p: Page) {
  const overflow = await p.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, "no horizontal scroll").toBeLessThanOrEqual(0);
}

async function expectNoAxeViolations(p: Page) {
  const results = await new AxeBuilder({ page: p })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .exclude("nextjs-portal")
    .analyze();
  expect(results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes[0]?.target.join(" ")}`)).toEqual([]);
}

test.describe("public pages", () => {
  for (const path of ["/", "/find-care", "/about", "/services"]) {
    test(`${path} fits, has one h1, and passes WCAG AA checks`, async ({ page: p }) => {
      await stubApi(p);
      await p.goto(path);
      await expect(p.getByRole("heading", { level: 1 })).toHaveCount(1);
      if (path === "/" || path === "/find-care") await expect(p.getByText("Dr. Ama Kollie").first()).toBeVisible();
      await expectNoHorizontalScroll(p);
      await expectNoAxeViolations(p);
    });
  }

  test("an unknown address answers 404 with a branded page and a way back", async ({ page: p }) => {
    await stubApi(p);
    const response = await p.goto("/no-such-page");
    expect(response?.status()).toBe(404);
    await expect(p.getByRole("heading", { level: 1, name: "Page not found" })).toBeVisible();
    await expect(p.getByRole("main").getByRole("link", { name: "Go to the homepage" })).toHaveAttribute("href", "/");
    await expect(p.getByRole("banner")).toBeVisible();
    await expectNoHorizontalScroll(p);
    await expectNoAxeViolations(p);
  });

  test("every internal link on the public pages resolves (no dead links)", async ({ page: p, request }) => {
    await stubApi(p);
    const hrefs = new Set<string>();
    for (const path of ["/", "/about", "/services", "/find-care"]) {
      await p.goto(path);
      await expect(p.getByRole("contentinfo")).toBeVisible();
      for (const href of await p.locator("a[href^='/']").evaluateAll((links) => links.map((a) => a.getAttribute("href")!))) {
        hrefs.add(href.split("#")[0].split("?")[0] || "/");
      }
    }
    for (const href of hrefs) {
      if (href.startsWith("/api/")) continue;
      expect((await request.get(href)).status(), href).toBe(200);
    }
    // Every reference footer link is present and routed (placeholders where content is pending).
    const footer = p.getByRole("contentinfo");
    for (const [name, href] of [
      ["For providers", "/for-providers"],
      ["Contact", "/contact"],
      ["Help center", "/help"],
      ["Privacy", "/privacy"],
      ["Accessibility", "/accessibility"],
      ["Terms of service", "/terms"],
    ]) {
      await expect(footer.getByRole("link", { name, exact: true })).toHaveAttribute("href", href);
    }
  });

  test("placeholder pages say their content is pending and invent nothing", async ({ page: p }) => {
    await stubApi(p);
    for (const path of ["/for-providers", "/help", "/contact", "/privacy", "/accessibility", "/terms"]) {
      await p.goto(path);
      await expect(p.getByRole("heading", { level: 1 })).toBeVisible();
      await expect(p.getByText("Its content has not been approved for publication yet.")).toBeVisible();
      await expectNoHorizontalScroll(p);
      await expectNoAxeViolations(p);
    }
  });

  test("homepage shows only real specialties, real providers and no fees", async ({ page: p }) => {
    await stubApi(p);
    await p.goto("/");
    const categories = p.getByRole("region", { name: "Start with the right kind of care" });
    await expect(categories.getByRole("link", { name: /Family medicine.*2 approved providers/ })).toHaveAttribute(
      "href",
      "/find-care?specialty=Family%20medicine",
    );
    await expect(categories.getByRole("link", { name: /Pediatrics.*1 approved provider/ })).toBeVisible();
    await expect(categories.getByRole("link", { name: /Laboratory requests/ })).toHaveAttribute("href", "/services#laboratory");
    const text = await p.locator("main").innerText();
    expect(text).not.toMatch(/USD|Video consultation|payout|Profile verified/);
  });

  test("the directory being down shows error states, not empty promises", async ({ page: p }) => {
    await stubApi(p, { down: true });
    await p.goto("/");
    await expect(p.getByText("Providers could not be loaded")).toBeVisible();
    await p.goto("/find-care");
    await expect(p.getByText("Providers could not be loaded")).toBeVisible();
    await expect(p.getByRole("button", { name: "Try again" })).toBeVisible();
  });

  test("open times drawer: keyboard, Escape and focus", async ({ page: p }) => {
    await stubApi(p);
    await p.goto("/find-care");
    const trigger = p.getByRole("link", { name: "See open times: Dr. Ama Kollie" });
    await trigger.focus();
    await p.keyboard.press("Enter");
    const drawer = p.getByRole("dialog", { name: "Open times · Dr. Ama Kollie" });
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole("radio")).toHaveCount(2);
    await expectNoAxeViolations(p);

    await drawer.getByRole("radio").first().focus();
    await p.keyboard.press("Space");
    await expect(drawer.getByRole("radio").first()).toBeChecked();
    await expect(drawer.getByRole("link", { name: "Sign in to request this time" })).toHaveAttribute(
      "href",
      `/sign-in?redirect=${encodeURIComponent("/find-care?provider=p1&slot=s1")}`,
    );
    await p.keyboard.press("Escape");
    await expect(drawer).toBeHidden();
    await expect(p).toHaveURL(/\/find-care$/);
    await expectNoHorizontalScroll(p);
  });

  test("hero search works without JavaScript-only behaviour (plain GET form)", async ({ page: p }) => {
    await stubApi(p);
    await p.goto("/");
    const form = p.getByRole("search", { name: "Find a doctor" });
    await expect(form).toHaveAttribute("action", "/find-care");
    await form.getByRole("searchbox", { name: "What care do you need?" }).fill("Pediatrics");
    await form.getByRole("searchbox").press("Enter");
    await expect(p).toHaveURL(/\/find-care\?search=Pediatrics$/);
  });
});
