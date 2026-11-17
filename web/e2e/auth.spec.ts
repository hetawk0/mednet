import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

/**
 * Layout, accessibility and keyboard checks for the account-access pages. The API is stubbed in
 * the browser so these run without a backend; real integration is covered by auth.live.spec.ts.
 */
async function stubAuthApi(page: Page, { google = true, holdSession }: { google?: boolean; holdSession?: Promise<void> } = {}) {
  await page.route("**/api/v1/auth/session", async (route) => {
    if (holdSession) await holdSession;
    await route.fulfill({
      status: 401,
      json: { success: false, error: { code: "UNAUTHORIZED", message: "Sign-in required", details: [] } },
    });
  });
  await page.route("**/api/v1/auth/google/status", (route) => route.fulfill({ json: { enabled: google } }));
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({ json: { headerName: "X-XSRF-TOKEN", token: "test-token" } }),
  );
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow, "page should not scroll horizontally").toBeLessThanOrEqual(0);
}

async function expectNoAxeViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .exclude("nextjs-portal")
    .analyze();
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes[0]?.target.join(" ")}`);
  expect(summary).toEqual([]);
}

/** Every control a person can reach must be at least 40px tall (44px for primary inputs/buttons). */
async function expectComfortableTargets(page: Page) {
  const small = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>("main a, main button, main input:not([type=radio])")]
      .filter((el) => el.offsetParent !== null)
      .map((el) => ({ text: el.textContent?.trim() || el.getAttribute("aria-label") || el.tagName, h: el.getBoundingClientRect().height }))
      .filter(({ h }) => h < 40),
  );
  expect(small).toEqual([]);
}

const pages = [
  { path: "/sign-in", heading: "Sign in securely.", ready: "Sign in to MedNet" },
  { path: "/sign-up/provider", heading: "Join MedNet as a healthcare provider.", ready: "Create your provider account" },
  { path: "/reset", heading: "Reset your password.", ready: "Reset your password" },
  { path: "/reset?email=patient%40example.test", heading: "Reset your password.", ready: "Enter your recovery code" },
  // Without a legacy link token the page goes straight to the six-digit code form.
  { path: "/verify", heading: "Confirm your email.", ready: "Email verification" },
  { path: "/account", heading: "Your MedNet account.", ready: "Sign in to continue" },
];

test.describe("account access pages", () => {
  for (const { path, heading, ready } of pages) {
    test(`${path} fits the viewport and passes WCAG AA checks`, async ({ page }) => {
      await stubAuthApi(page);
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
      await expect(page.getByText(ready, { exact: true }).first()).toBeVisible();
      await expect(page.getByRole("banner")).toBeVisible();
      await expect(page.getByRole("contentinfo")).toContainText("MedNet does not replace emergency services.");
      await expectNoHorizontalScroll(page);
      await expectComfortableTargets(page);
      await expectNoAxeViolations(page);
    });
  }

  test("sign-in shows a loading state while the session is checked", async ({ page }) => {
    let release = () => {};
    await stubAuthApi(page, { holdSession: new Promise<void>((resolve) => (release = resolve)) });
    await page.goto("/sign-in");
    await expect(page.getByRole("status").filter({ hasText: "Checking your session…" })).toBeVisible();
    release();
    await expect(page.getByRole("heading", { name: "Sign in to MedNet" })).toBeVisible();
  });

  test("every sign-in mode passes WCAG AA checks", async ({ page }) => {
    await stubAuthApi(page);
    await page.goto("/sign-in");
    // Spring Security's default authorization endpoint, proxied to the API by next.config and LPAD.
    await expect(page.getByRole("link", { name: "Continue with Google" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );

    await page.locator("label").filter({ hasText: "Create account" }).click();
    await expect(page.getByRole("heading", { name: "Create a patient account" })).toBeVisible();
    await expect(page.getByText("At least 12 characters.")).toBeVisible();
    await expectNoAxeViolations(page);

    await page.locator("label").filter({ hasText: "Sign in" }).click();
    await page.getByRole("button", { name: "Forgot password?" }).click();
    await expect(page.getByRole("heading", { name: "Recover your password" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Password" })).toHaveCount(0);
    await expectNoAxeViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test("the Google error notice is announced and accessible", async ({ page }) => {
    await stubAuthApi(page, { google: false });
    // SecurityConfig's Google failure handler redirects here; with Google disabled no Google link is offered.
    await page.goto("/sign-in?googleError=1");
    await expect(page.getByRole("alert").filter({ hasText: "Google sign-in could not be completed" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Continue with Google" })).toHaveCount(0);
    await expectNoAxeViolations(page);
  });

  test("keyboard: skip link, mode switch, validation focus and visible focus ring", async ({ page }) => {
    await stubAuthApi(page);
    await page.goto("/sign-in");
    await expect(page.getByRole("heading", { name: "Sign in to MedNet" })).toBeVisible();

    await page.keyboard.press("Tab");
    const skip = page.getByRole("link", { name: "Skip to content" });
    await expect(skip).toBeFocused();
    await expect(skip).toBeVisible();
    await page.keyboard.press("Enter");

    const signInRadio = page.getByRole("radio", { name: "Sign in" });
    await signInRadio.focus();
    await page.keyboard.press("ArrowRight");
    await expect(page.getByRole("radio", { name: "Create account" })).toBeChecked();
    await expect(page.getByRole("heading", { name: "Create a patient account" })).toBeVisible();
    await page.keyboard.press("ArrowLeft");
    await expect(signInRadio).toBeChecked();

    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    const email = page.getByRole("textbox", { name: "Email address" });
    await expect(email).toBeFocused();
    await expect(email).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByText("Enter your email address.")).toBeVisible();

    await email.fill("patient@example.test");
    await page.keyboard.press("Tab");
    const password = page.getByRole("textbox", { name: "Password", exact: true });
    await expect(password).toBeFocused();
    const outline = await password.evaluate((el) => getComputedStyle(el).outlineWidth);
    expect(parseFloat(outline)).toBeGreaterThanOrEqual(2);
  });

  test("reset code input accepts digits only and validates before calling the API", async ({ page }) => {
    await stubAuthApi(page);
    let verifyCalls = 0;
    await page.route("**/api/v1/auth/reset-password/verify", (route) => {
      verifyCalls += 1;
      return route.fulfill({ status: 400, json: { token: null, message: "The code is invalid or expired. Request a new code." } });
    });
    await page.goto("/reset?email=patient%40example.test");
    const code = page.getByRole("textbox", { name: "Six-digit code" });
    await code.fill("12ab3");
    await expect(code).toHaveValue("123");
    await page.getByRole("button", { name: "Verify code" }).click();
    await expect(page.getByText("Enter the six-digit code from your email.")).toBeVisible();
    await expect(code).toBeFocused();
    expect(verifyCalls).toBe(0);

    await code.fill("123456");
    await page.getByRole("button", { name: "Verify code" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "The code is invalid or expired" })).toBeVisible();
    expect(verifyCalls).toBe(1);
    await expectNoAxeViolations(page);
  });

  test("provider sign-up registers a PROVIDER account and continues to email verification", async ({ page }) => {
    await stubAuthApi(page);
    const bodies: unknown[] = [];
    await page.route("**/api/v1/auth/register", async (route) => {
      bodies.push(route.request().postDataJSON());
      await route.fulfill({ status: 202, json: { message: "Check your email." } });
    });
    await page.goto("/sign-in?mode=register");
    await page.getByRole("link", { name: "sign up as a healthcare provider" }).click();
    await expect(page).toHaveURL(/\/sign-up\/provider$/);
    await expect(page.getByRole("list", { name: "Provider sign-up steps" }).locator("li[aria-current=step]")).toContainText(
      "Create your account",
    );

    await page.getByRole("button", { name: "Create provider account" }).click();
    await expect(page.getByRole("textbox", { name: "Professional email address" })).toBeFocused();
    await page.getByRole("textbox", { name: "Professional email address" }).fill("doctor@example.test");
    await page.getByRole("textbox", { name: "Password", exact: true }).fill("a-valid-password-123");
    await page.getByRole("button", { name: "Create provider account" }).click();

    await expect(page).toHaveURL(/\/verify\?email=doctor%40example\.test$/);
    expect(bodies).toEqual([{ email: "doctor@example.test", password: "a-valid-password-123", accountType: "PROVIDER" }]);
  });

  test("provider sign-up with a registered email offers to continue the existing application", async ({ page }) => {
    await stubAuthApi(page);
    await page.route("**/api/v1/auth/register", (route) =>
      route.fulfill({ status: 409, json: { success: false, error: { code: "CONFLICT", message: "An account with this email already exists." } } }),
    );
    await page.goto("/sign-up/provider");
    await page.getByRole("textbox", { name: "Professional email address" }).fill("doctor@example.test");
    await page.getByRole("textbox", { name: "Password", exact: true }).fill("a-valid-password-123");
    await page.getByRole("button", { name: "Create provider account" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "An account with this email already exists." })).toBeVisible();
    await expect(page.getByRole("link", { name: "Sign in to continue", exact: true })).toHaveAttribute("href", "/sign-in?redirect=%2Fprovider");
    await expectNoAxeViolations(page);
  });
});
