import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { stubPatientApi } from "./support/patient-stub";

/**
 * Patient workspace (Phase 4C) with the API stubbed in the browser: layout at 390/820/1440,
 * WCAG A/AA, the session guard, and each write sends the right request with CSRF.
 */
async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, "page should not scroll horizontally").toBeLessThanOrEqual(0);
}

async function expectNoAxeViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .exclude("nextjs-portal")
    .analyze();
  expect(results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes[0]?.target.join(" ")}`)).toEqual([]);
}

async function expectComfortableTargets(page: Page) {
  const small = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>("main a, main button, main input:not([type=radio]), main select, header button")]
      .filter((el) => el.offsetParent !== null)
      // A checkbox's target is its wrapping label row (design-system Checkbox: 44px row), not the native box.
      .map((el) => ({ el, target: (el instanceof HTMLInputElement && el.type === "checkbox" && el.closest("label")) || el }))
      .map(({ el, target }) => ({ text: el.textContent?.trim() || el.getAttribute("aria-label") || el.tagName, h: target.getBoundingClientRect().height }))
      .filter(({ h }) => h < 40),
  );
  expect(small).toEqual([]);
}

const views = [
  { path: "/patient", title: /^Good (morning|afternoon|evening), James$/, ready: "Dr. Mariama Cole" },
  { path: "/patient/find-a-doctor", title: "Find a doctor", ready: "Approved providers" },
  { path: "/patient/appointments", title: "Appointments", ready: "A new time was proposed" },
  { path: "/patient/appointments?tab=home-care", title: "Appointments", ready: "Wound dressing" },
  { path: "/patient/appointments/consultation?appointment=apt-next", title: "Text consultation", ready: "Join the text consultation" },
  { path: "/patient/messages?conversation=conv-1", title: "Messages", ready: "How are you feeling after the visit?" },
  { path: "/patient/records", title: "Medical information", ready: "Follow-up consultation" },
  { path: "/patient/records?tab=laboratory", title: "Medical information", ready: "Released results appear under Laboratory results" },
  { path: "/patient/records?tab=medications", title: "Medical information", ready: "Amoxicillin · 1 capsule" },
  { path: "/patient/records?tab=vitals", title: "Medical information", ready: "72.5 kg" },
  { path: "/patient/records?tab=sharing", title: "Medical information", ready: "Dr. Samuel Kollie" },
  { path: "/patient/payments", title: "Payments", ready: "Payments are not available on MedNet" },
  { path: "/patient/help", title: "Help & support", ready: "Appointment help" },
  { path: "/patient/profile", title: "Your profile", ready: "Your details" },
];

test.describe("patient workspace", () => {
  for (const { path, title, ready } of views) {
    test(`${path} fits the viewport and passes WCAG AA checks`, async ({ page }) => {
      await stubPatientApi(page);
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();
      await expect(page.getByText(ready).first()).toBeVisible();
      await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
      await expectNoHorizontalScroll(page);
      await expectComfortableTargets(page);
      await expectNoAxeViolations(page);
    });
  }

  test("signed-out visitors are asked to sign in and return to the same page", async ({ page }) => {
    await stubPatientApi(page, { signedOut: true });
    await page.goto("/patient/records?tab=vitals");
    const link = page.getByRole("link", { name: "Go to sign in" });
    await expect(link).toHaveAttribute("href", "/sign-in?redirect=%2Fpatient%2Frecords%3Ftab%3Dvitals");
    await expectNoAxeViolations(page);
  });

  test("other roles are sent to their own home", async ({ page }) => {
    await stubPatientApi(page, { role: "HOME_CARE" });
    await page.route("**/home-care", (route) => route.fulfill({ status: 200, contentType: "text/html", body: "<h1>Home care</h1>" }));
    await page.goto("/patient");
    await expect(page).toHaveURL(/\/home-care$/);
  });

  test("an unreachable API shows an error with retry", async ({ page }) => {
    await stubPatientApi(page, { apiDown: true });
    await page.goto("/patient");
    await expect(page.getByRole("alert").filter({ hasText: "The MedNet API is unavailable" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
  });

  test("overview shows live data and no invented fees", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient");
    const next = page.locator("section").filter({ has: page.getByText("Next appointment", { exact: true }) });
    await expect(next).toContainText("Dr. Mariama Cole");
    await expect(next).toContainText("Text consultation");
    await expect(next).toContainText("30 minutes");
    await expect(next).not.toContainText("USD");
    await expect(page.getByRole("link", { name: /Review a proposed new time/ })).toHaveAttribute("href", "/patient/appointments");
    // The only amount on the page is the labelled presentation-only receipt.
    const payments = page.locator("section", { hasText: "Latest receipt" });
    await expect(payments.getByText("Fictional data")).toBeVisible();
    await expect(page.getByText("Video consultation")).toHaveCount(0);
  });

  test("a missing profile becomes the first action", async ({ page }) => {
    await stubPatientApi(page, { noProfile: true });
    await page.goto("/patient");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(/^Good (morning|afternoon|evening)$/);
    await expect(page.getByRole("link", { name: /Complete your patient profile/ })).toHaveAttribute("href", "/patient/profile");
  });

  test("empty account shows empty states", async ({ page }) => {
    await stubPatientApi(page, { empty: true });
    await page.goto("/patient");
    await expect(page.getByRole("heading", { name: "No upcoming appointments", exact: true })).toBeVisible();
    await expect(page.getByText("No records yet.", { exact: false })).toBeVisible();
    await expect(page.getByText("No notifications yet.")).toBeVisible();
  });

  test("cancel an appointment after confirming", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/appointments");
    await page.getByRole("button", { name: "Cancel appointment with Dr. Mariama Cole" }).click();
    const dialog = page.getByRole("dialog", { name: "Cancel this appointment?" });
    await expect(dialog.getByRole("button", { name: "Cancel", exact: true })).toBeFocused();
    await dialog.getByRole("button", { name: "Cancel appointment" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Appointment cancelled" })).toBeVisible();
    expect(log.requests).toContainEqual({ method: "PATCH", path: "/appointments/apt-next", body: { action: "CANCEL" }, csrf: "test-token" });
  });

  test("accept a proposed new time", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/appointments?appointment=apt-moved");
    await expect(page.locator("#appointment-apt-moved")).toHaveAttribute("aria-current", "true");
    await expect(page.getByRole("dialog", { name: "Appointment details" })).toContainText("A new time was proposed.");
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Accept new time" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Accept new time" }).click();
    await expect(page.getByText("New time accepted.")).toBeVisible();
    await expect(page.getByText("A new time was proposed")).toHaveCount(0);
    expect(log.requests.at(-1)).toMatchObject({ path: "/appointments/apt-moved", body: { action: "ACCEPT_RESCHEDULE" } });
  });

  test("request and cancel a home-care visit", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/appointments?tab=home-care");
    await page.getByRole("button", { name: "Cancel request: Wound dressing" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Cancel request" }).click();
    await expect(page.getByText("Request cancelled.")).toBeVisible();
    expect(log.requests).toContainEqual(expect.objectContaining({ method: "PATCH", path: "/home-care-requests/req-home/cancel" }));

    await page.getByRole("button", { name: "Send request" }).click();
    await expect(page.getByLabel("Care needed")).toBeFocused();
    await expect(page.getByText("Describe the care you need.")).toBeVisible();
  });

  test("request a laboratory test", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/records?tab=laboratory");
    await page.getByLabel("Test needed").fill("Malaria rapid test");
    await page.getByRole("button", { name: "Send request" }).click();
    await expect(page.getByText("Request sent.")).toBeVisible();
    await expect(page.getByText("Malaria rapid test")).toBeVisible();
    expect(log.requests).toContainEqual({ method: "POST", path: "/lab-requests", body: { testDescription: "Malaria rapid test" }, csrf: "test-token" });
  });

  test("allow a doctor to see records", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/records?tab=sharing");
    await page.getByRole("button", { name: "Allow Dr. Samuel Kollie to see your records" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Allow access" }).click();
    await expect(page.getByText("Dr. Samuel Kollie can now see your records.")).toBeVisible();
    expect(log.requests).toContainEqual({ method: "POST", path: "/patients/me/record-consents", body: { providerId: "prov-kollie" }, csrf: "test-token" });
  });

  test("send a message", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/messages?conversation=conv-1");
    await page.getByLabel("Your message").fill("Much better, thank you.");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByRole("list", { name: "Messages with Dr. Mariama Cole" })).toContainText("Much better, thank you.");
    expect(log.requests).toContainEqual({ method: "POST", path: "/conversations/conv-1/messages", body: { body: "Much better, thank you." }, csrf: "test-token" });
  });

  test("profile validates before saving and updates the greeting", async ({ page }) => {
    const log = await stubPatientApi(page, { noProfile: true });
    await page.goto("/patient/profile");
    await page.getByRole("button", { name: "Save profile" }).click();
    await expect(page.getByLabel("Full name")).toBeFocused();
    expect(log.requests.filter((r) => r.method === "PUT")).toHaveLength(0);
    await page.getByLabel("Full name").fill("Ama Doe");
    await page.getByLabel("Date of birth").fill("1992-01-05");
    await page.getByLabel("Phone number").fill("+231 880 000 000");
    await page.getByLabel("Address").fill("Paynesville");
    await page.getByRole("button", { name: "Save profile" }).click();
    await expect(page.getByText("Your profile was saved.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ method: "PUT", path: "/patients/me/profile", csrf: "test-token" });
    await page.getByRole("link", { name: "Overview" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(/, Ama$/);
  });

  test("account menu signs out", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/help");
    await page.getByRole("button", { name: "Account" }).click();
    await page.getByRole("menuitem", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/sign-in$/);
    expect(log.requests).toContainEqual(expect.objectContaining({ method: "POST", path: "/auth/logout" }));
  });

  test("patients who applied to provide care get a link to their application status", async ({ page }) => {
    await stubPatientApi(page, { providerApplication: "PENDING" });
    await page.goto("/patient/help");
    await page.getByRole("button", { name: "Account" }).click();
    await page.getByRole("menuitem", { name: "Provider application status" }).click();
    await expect(page).toHaveURL(/\/provider$/);
    await expect(page.getByText("Under review").first()).toBeVisible();
    // Applicants see their status only, never the doctor workspace navigation.
    await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toHaveCount(0);
  });

  test("patients who never applied have no application link", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient/help");
    await page.getByRole("button", { name: "Account" }).click();
    await expect(page.getByRole("menuitem", { name: "Sign out" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "Provider application status" })).toHaveCount(0);
  });

  test("overview details link opens the appointment details", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient");
    await page.getByRole("link", { name: "View details" }).click();
    const drawer = page.getByRole("dialog", { name: "Appointment details" });
    await expect(drawer).toContainText("Dr. Mariama Cole");
    await expect(drawer).toContainText("Liberia time (GMT)");
    await expect(drawer).toContainText("The doctor confirmed this appointment.");
    await expect(drawer.getByRole("link", { name: "Text consultation" })).toHaveAttribute(
      "href",
      "/patient/appointments/consultation?appointment=apt-next",
    );
    await expectNoAxeViolations(page);
    await page.keyboard.press("Escape");
    await expect(drawer).toBeHidden();
  });

  test("join a text consultation only on request, then send a message", async ({ page }) => {
    const log = await stubPatientApi(page);
    await page.goto("/patient/appointments/consultation?appointment=apt-next");
    await expect(page.getByRole("button", { name: "Join text consultation" })).toBeVisible();
    expect(log.requests.filter((r) => r.path.endsWith("/consultation"))).toHaveLength(0);
    await page.getByRole("button", { name: "Join text consultation" }).click();
    await expect(page).toHaveURL(/consultation\?consultation=con-apt-next$/);
    await page.getByLabel("Your message").fill("I am ready.");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByRole("list", { name: "Consultation messages" })).toContainText("I am ready.");
    expect(log.requests).toContainEqual({ method: "POST", path: "/appointments/apt-next/consultation", body: undefined, csrf: "test-token" });
    expect(log.requests).toContainEqual({ method: "POST", path: "/consultations/con-apt-next/messages", body: { body: "I am ready." }, csrf: "test-token" });
  });

  test("in-person appointments do not offer a text consultation", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient/appointments/consultation?appointment=apt-moved");
    await expect(page.getByRole("heading", { name: "Text consultation not available" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Join text consultation" })).toHaveCount(0);
  });

  test("an expired session hides patient data and offers sign-in back to the page", async ({ page }) => {
    const stub = await stubPatientApi(page);
    await page.goto("/patient/records");
    await expect(page.getByText("Follow-up consultation")).toBeVisible();
    stub.expireSession();
    await page.getByRole("tab", { name: "Vitals" }).click();
    await expect(page.getByText("Your session has ended", { exact: true })).toBeVisible();
    await expect(page.getByText("Follow-up consultation")).toHaveCount(0);
    await expect(page.getByRole("navigation", { name: "Patient workspace" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Sign in again" })).toHaveAttribute(
      "href",
      "/sign-in?redirect=%2Fpatient%2Frecords%3Ftab%3Dvitals",
    );
  });

  test("an expired session during an action is handled the same way", async ({ page }) => {
    const stub = await stubPatientApi(page);
    await page.goto("/patient/messages?conversation=conv-1");
    await expect(page.getByText("How are you feeling after the visit?")).toBeVisible();
    stub.expireSession();
    await page.getByLabel("Your message").fill("Hello");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByText("Your session has ended", { exact: true })).toBeVisible();
    await expect(page.getByText("How are you feeling after the visit?")).toHaveCount(0);
  });

  test("a permission refusal shows a denied state without retry", async ({ page }) => {
    await stubPatientApi(page, { forbidden: ["/patients/me/records"] });
    await page.goto("/patient/records");
    await expect(page.getByText("You do not have permission to see this information.")).toBeVisible();
    await expect(page.getByRole("button", { name: "Try again" })).toHaveCount(0);
  });

  test("another patient's appointment link shows not found", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient/appointments?appointment=someone-else");
    await expect(page.getByRole("dialog", { name: "Appointment details" })).toContainText("Appointment not found");
  });

  test("find a doctor keeps the workspace path", async ({ page }) => {
    await stubPatientApi(page);
    await page.goto("/patient/find-a-doctor");
    await page.getByRole("searchbox", { name: "Doctor name or specialty" }).fill("Cole");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page).toHaveURL(/\/patient\/find-a-doctor\?search=Cole$/);
    await expect(page.getByRole("link", { name: "Patient workspace" }).or(page.getByRole("navigation", { name: "Patient workspace" }))).toBeVisible();
  });
});
