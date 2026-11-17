import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { stubProviderApi } from "./support/provider-stub";

/**
 * Doctor workspace (Phase 4D) with the API stubbed in the browser: layout at 390/820/1440, WCAG A/AA,
 * application states, and every write sends the right request with CSRF.
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
  { path: "/provider", title: /^Good (morning|afternoon|evening), Dr\. Cole$/, ready: "Today’s schedule" },
  { path: "/provider/schedule", title: "Schedule", ready: "Publish an open time" },
  { path: "/provider/appointments", title: "Appointments", ready: "Joseph Flomo" },
  { path: "/provider/appointments?tab=upcoming", title: "Appointments", ready: "Upcoming appointments" },
  { path: "/provider/appointments?tab=history", title: "Appointments", ready: "Completed and closed" },
  { path: "/provider/appointments/consultation?appointment=apt-today", title: "Text consultation", ready: "Open the text consultation" },
  { path: "/provider/patients", title: "Patients", ready: "Your patients" },
  { path: "/provider/patients/pat-james", title: "Patient", ready: "Follow-up consultation" },
  { path: "/provider/patients/pat-james?tab=results", title: "Patient", ready: "Release to patient" },
  { path: "/provider/patients/pat-evelyn", title: "Patient", ready: "The patient has not allowed you to see their records" },
  { path: "/provider/clinical-work", title: "Clinical work", ready: "Records not shared with you" },
  { path: "/provider/messages?conversation=conv-1", title: "Messages", ready: "Can I take the medicine with food?" },
  { path: "/provider/earnings", title: "Earnings & payouts", ready: "Earnings and payouts are not available on MedNet" },
  { path: "/provider/help", title: "Help & support", ready: "Open times and requests" },
  { path: "/provider/profile", title: "Your profile", ready: "Credential reference you submitted" },
];

test.describe("doctor workspace", () => {
  for (const { path, title, ready } of views) {
    test(`${path} fits the viewport and passes WCAG AA checks`, async ({ page }) => {
      await stubProviderApi(page);
      await page.goto(path);
      await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();
      await expect(page.getByText(ready).first()).toBeVisible();
      await expect(page.getByRole("heading", { level: 1 })).toHaveCount(1);
      await expectNoHorizontalScroll(page);
      await expectComfortableTargets(page);
      await expectNoAxeViolations(page);
    });
  }

  test("overview uses real data, labels the earnings card, and never says verified", async ({ page }) => {
    await stubProviderApi(page);
    await page.goto("/provider");
    await expect(page.getByRole("link", { name: /Requests to answer/ })).toContainText("2");
    await expect(page.getByRole("list", { name: "Today’s schedule" })).toContainText("James Brown");
    await expect(page.locator("section", { hasText: "Provider financial summary" }).getByText("Fictional data")).toBeVisible();
    await expect(page.getByText(/verified/i)).toHaveCount(0);
    await expect(page.getByText(/video/i)).toHaveCount(0);
  });

  test("confirm and decline requests", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/appointments");
    await page.getByRole("button", { name: "Confirm request from Joseph Flomo" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Confirm appointment" }).click();
    await expect(page.getByText("Appointment confirmed. The patient has been notified.")).toBeVisible();
    expect(log.requests).toContainEqual({ method: "PATCH", path: "/appointments/apt-request", body: { action: "ACCEPT" }, csrf: "test-token" });

    await page.getByRole("button", { name: "Accept new time" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Accept new time" }).click();
    await expect(page.getByText("New time accepted.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ path: "/appointments/apt-moved", body: { action: "ACCEPT_RESCHEDULE" } });
  });

  test("propose a new time from open times", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/appointments?tab=upcoming");
    await page.getByRole("button", { name: "Propose a new time to James Brown" }).click();
    const dialog = page.getByRole("dialog", { name: "Propose a new time" });
    await dialog.getByRole("button", { name: "Propose this time" }).click();
    await expect(dialog.getByText("Choose a new time.")).toBeVisible();
    await dialog.getByRole("radio").first().check();
    await dialog.getByRole("button", { name: "Propose this time" }).click();
    await expect(page.getByText(/New time proposed/)).toBeVisible();
    await expect(page.getByText("Waiting for the patient to accept or decline.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ path: "/appointments/apt-today", body: { action: "REQUEST_RESCHEDULE", proposedAvailabilitySlotId: "slot-1" } });
    await expectNoAxeViolations(page);
  });

  test("mark an ended appointment completed", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/appointments?tab=history");
    await page.getByRole("button", { name: "Mark appointment with James Brown completed" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Mark completed" }).click();
    await expect(page.getByText("Appointment marked as completed.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ path: "/appointments/apt-ended", body: { action: "COMPLETE" } });
  });

  test("publish, reject overlap, and close open times", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/schedule");
    await page.getByRole("button", { name: "Publish time" }).click();
    await expect(page.getByLabel(/^Start time/)).toBeFocused();
    const later = new Date(Date.now() + 10 * 86_400_000).toISOString().slice(0, 10);
    await page.getByLabel(/^Date/).fill(later);
    await page.getByLabel(/^Start time/).fill("09:00");
    await page.getByRole("button", { name: "Publish time" }).click();
    await expect(page.getByText(/^Published /)).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ method: "POST", path: "/providers/me/availability", body: { startsAt: `${later}T09:00:00.000Z`, endsAt: `${later}T09:30:00.000Z`, consultationMode: "TEXT" }, csrf: "test-token" });

    await page.getByLabel(/^Start time/).fill("09:15");
    await page.getByRole("button", { name: "Publish time" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "Availability slots may not overlap" })).toBeVisible();

    await page.getByRole("button", { name: /^Close / }).first().click();
    await page.getByRole("dialog").getByRole("button", { name: "Close time" }).click();
    await expect(page.getByText(/^Closed /)).toBeVisible();
    expect(log.requests).toContainEqual(expect.objectContaining({ method: "DELETE", path: "/providers/me/availability/slot-1" }));
  });

  test("a requested time cannot be closed", async ({ page }) => {
    await stubProviderApi(page);
    await page.goto("/provider/schedule");
    await page.getByRole("button", { name: /^Close / }).nth(1).click();
    await page.getByRole("dialog").getByRole("button", { name: "Close time" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "Reserved availability cannot be closed" })).toBeVisible();
  });

  test("open, write in and end a text consultation", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/appointments/consultation?appointment=apt-today");
    await page.getByRole("button", { name: "Open text consultation" }).click();
    await expect(page).toHaveURL(/consultation\?consultation=con-apt-today$/);
    await page.getByLabel("Your message").fill("Hello James, how are you feeling?");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByRole("list", { name: "Consultation messages" })).toContainText("Hello James");
    await page.getByRole("button", { name: "End consultation" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "End consultation" }).click();
    await expect(page.getByText("This consultation has ended and can no longer receive messages.")).toBeVisible();
    await expect(page.getByLabel("Your message")).toHaveCount(0);
    expect(log.requests).toContainEqual(expect.objectContaining({ method: "PATCH", path: "/consultations/con-apt-today/end", csrf: "test-token" }));
  });

  test("document a consultation and add a correction", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/patients/pat-james");
    await page.getByRole("button", { name: "Add record" }).click();
    await expect(page.getByLabel(/^Title/)).toBeFocused();
    await page.getByLabel(/^Title/).fill("Text consultation summary");
    await page.getByLabel(/^Clinical note/).fill("Symptoms improving. Continue current plan.");
    await page.getByRole("button", { name: "Add record" }).click();
    await expect(page.getByText("Record added to the patient’s file.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ method: "POST", path: "/patients/pat-james/records", body: { category: "ENCOUNTER_SUMMARY", title: "Text consultation summary" } });

    await page.getByText("Follow-up consultation").click();
    await page.getByRole("button", { name: "Add a correction" }).click();
    await page.getByLabel(/^Clinical note/).fill("Corrected follow-up date.");
    await page.getByRole("button", { name: "Add correction" }).click();
    await expect(page.getByText("Correction added. The original record is kept.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ body: { amendsRecordId: "rec-1" } });
  });

  test("release a laboratory result", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/patients/pat-james?tab=results");
    await page.getByRole("button", { name: "Release to patient" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Release result" }).click();
    await expect(page.getByText(/Result released/)).toBeVisible();
    await expect(page.getByText("No results waiting for review.")).toBeVisible();
    expect(log.requests.at(-1)).toMatchObject({ method: "PATCH", path: "/lab-results/lab-1/review", body: { action: "RELEASE" } });
  });

  test("a patient without consent shows no clinical data or forms", async ({ page }) => {
    await stubProviderApi(page);
    await page.goto("/provider/patients/pat-evelyn");
    await expect(page.getByText("The patient has not allowed you to see their records")).toBeVisible();
    await expect(page.getByRole("button", { name: "Add record" })).toHaveCount(0);
  });

  test("a request-only patient cannot be opened from the list", async ({ page }) => {
    await stubProviderApi(page);
    await page.goto("/provider/patients");
    await expect(page.getByRole("link", { name: "Open Joseph Flomo" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Open James Brown" })).toBeVisible();
  });

  test("reply to a patient message", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/messages?conversation=conv-1");
    await page.getByLabel("Your message").fill("Yes, with food is fine.");
    await page.getByRole("button", { name: "Send message" }).click();
    await expect(page.getByRole("list", { name: "Messages with James Brown" })).toContainText("Yes, with food is fine.");
    expect(log.requests).toContainEqual({ method: "POST", path: "/conversations/conv-1/messages", body: { body: "Yes, with food is fine." }, csrf: "test-token" });
  });

  // A provider session whose application changes after sign-in (for example, suspended by an administrator).
  for (const [status, text] of [
    ["PENDING", "Under review"],
    ["SUSPENDED", "Suspended"],
  ] as const) {
    test(`a provider session with a ${status.toLowerCase()} application sees only its status`, async ({ page }) => {
      await stubProviderApi(page, { application: status });
      await page.goto("/provider/schedule");
      await expect(page.getByText(text, { exact: true }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Publish time" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Submit application" })).toHaveCount(0);
      await expect(page.getByText(/verified/i)).toHaveCount(0);
      await expectNoAxeViolations(page);
    });
  }

  test("a failed application load can be retried in place (Phase 5E)", async ({ page }) => {
    await stubProviderApi(page);
    let failures = 1;
    await page.route("**/api/v1/providers/me/application", (route) => (failures-- > 0 ? route.fulfill({ status: 502, body: "" }) : route.fallback()));
    await page.goto("/provider/schedule");
    await expect(page.getByRole("alert").filter({ hasText: "Your provider application could not be loaded" })).toBeVisible();
    await page.getByRole("button", { name: "Try again" }).click();
    await expect(page.getByText("Publish an open time")).toBeVisible();
  });

  test("a provider account without an application can apply", async ({ page }) => {
    const log = await stubProviderApi(page, { application: "none" });
    await page.goto("/provider");
    await page.getByRole("button", { name: "Submit application" }).click();
    await expect(page.getByLabel(/^Display name/)).toBeFocused();
    await page.getByLabel(/^Display name/).fill("Dr. Ama Kollie");
    await page.getByLabel(/^Specialty/).fill("Pediatrics");
    await page.getByLabel(/^Licence or registration reference/).fill("LMDC-5678");
    await page.getByRole("button", { name: "Submit application" }).click();
    await expect(page.getByText("Under review", { exact: true }).first()).toBeVisible();
    expect(log.requests).toContainEqual({ method: "POST", path: "/providers/applications", body: { displayName: "Dr. Ama Kollie", specialty: "Pediatrics", credentialReference: "LMDC-5678" }, csrf: "test-token" });
  });

  test("a rejected application can apply again", async ({ page }) => {
    await stubProviderApi(page, { application: "REJECTED" });
    await page.goto("/provider");
    await expect(page.getByText("Not approved", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Submit application" })).toBeVisible();
  });

  test("an applicant (signed in as a patient until approved) sees the application status", async ({ page }) => {
    await stubProviderApi(page, { role: "PATIENT", application: "PENDING" });
    await page.goto("/provider/schedule");
    await expect(page.getByText("Under review", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Go to the patient workspace" })).toHaveAttribute("href", "/patient");
    await expectNoAxeViolations(page);
  });

  test("an application approved after sign-in asks for a new sign-in", async ({ page }) => {
    await stubProviderApi(page, { role: "PATIENT", application: "APPROVED" });
    await page.goto("/provider");
    await expect(page.getByText("Sign in again to open your doctor workspace")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
  });

  test("patients are sent to their own workspace", async ({ page }) => {
    await stubProviderApi(page, { role: "PATIENT", application: "none" });
    await page.route("**/patient", (route) => route.fulfill({ status: 200, contentType: "text/html", body: "<h1>Patient</h1>" }));
    await page.goto("/provider");
    await expect(page).toHaveURL(/\/patient$/);
  });

  test("an expired session hides the workspace", async ({ page }) => {
    const log = await stubProviderApi(page);
    await page.goto("/provider/patients/pat-james");
    await expect(page.getByText("Follow-up consultation")).toBeVisible();
    log.expireSession();
    await page.getByRole("tab", { name: "Vitals" }).click();
    await expect(page.getByText("Your session has ended", { exact: true })).toBeVisible();
    await expect(page.getByText("Follow-up consultation")).toHaveCount(0);
  });

  test("empty account shows empty states", async ({ page }) => {
    await stubProviderApi(page, { empty: true });
    await page.goto("/provider");
    await expect(page.getByText("No appointments today.")).toBeVisible();
    await expect(page.getByText("No requests are waiting.")).toBeVisible();
    await page.goto("/provider/schedule");
    await expect(page.getByText(/No open times\./)).toBeVisible();
  });
});

/**
 * Provider sign-up onboarding (PATIENT role + PROVIDER account type until an administrator approves).
 * The API refuses provider endpoints to these sessions; these checks prove the UI neither calls nor shows them.
 */
test.describe("provider onboarding", () => {
  const CLINICAL = /^\/(appointments|providers\/me\/(patients|availability|lab-review-queue)|patients\/|conversations|consultations)/;

  test("a provider sign-up is guided to submit professional details, then sees the review status", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "none" });
    await page.goto("/provider");
    await expect(page.getByRole("heading", { name: "Submit your professional details" })).toBeVisible();
    const steps = page.getByRole("list", { name: "Provider sign-up steps" });
    await expect(steps.locator("li[aria-current=step]")).toContainText("Submit your professional details");
    await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Go to the patient workspace" })).toHaveCount(0);
    await expectNoHorizontalScroll(page);
    await expectComfortableTargets(page);
    await expectNoAxeViolations(page);

    const form = page.getByRole("form", { name: "Professional details" });
    await form.getByLabel(/^Display name/).fill("Dr. Abena Sirleaf");
    await form.getByLabel(/^Specialty/).fill("Family medicine");
    await form.getByLabel(/^Licence or registration reference/).fill("LMDC-2041");
    await form.getByRole("button", { name: "Submit application" }).click();

    await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
    await expect(steps.locator("li[aria-current=step]")).toContainText("Administrator review");
    expect(log.requests.filter((r) => r.path === "/providers/applications")).toEqual([
      { method: "POST", path: "/providers/applications", body: { displayName: "Dr. Abena Sirleaf", specialty: "Family medicine", credentialReference: "LMDC-2041" }, csrf: "test-token" },
    ]);
    await expectNoAxeViolations(page);
    expect(log.reads.filter((path) => CLINICAL.test(path))).toEqual([]);
  });

  test("a pending provider opening a clinical page sees onboarding and no clinical data is requested", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "PENDING" });
    for (const path of ["/provider/patients", "/provider/schedule", "/provider/clinical-work"]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
      await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toHaveCount(0);
    }
    expect(log.reads.filter((path) => CLINICAL.test(path))).toEqual([]);
  });

  test("the patient workspace sends a provider sign-up to onboarding", async ({ page }) => {
    await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "PENDING" });
    await page.goto("/patient");
    await expect(page).toHaveURL(/\/provider$/);
    await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  });

  test("a rejected provider sign-up submits corrected details", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "REJECTED" });
    await page.goto("/provider");
    await expect(page.getByRole("heading", { name: "Your application was not approved" })).toBeVisible();
    await expectNoAxeViolations(page);
    const form = page.getByRole("form", { name: "Submit corrected details" });
    await form.getByLabel(/^Display name/).fill("Dr. Abena Sirleaf, MD");
    await form.getByLabel(/^Specialty/).fill("Family medicine");
    await form.getByLabel(/^Licence or registration reference/).fill("LMDC-2041");
    await form.getByRole("button", { name: "Submit corrected application" }).click();
    await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
    expect(log.requests.filter((r) => r.path === "/providers/applications")).toHaveLength(1);
  });

  test("a suspended provider cannot submit a new application", async ({ page }) => {
    await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "SUSPENDED" });
    await page.goto("/provider");
    await expect(page.getByRole("heading", { name: "Your provider listing is suspended" })).toBeVisible();
    await expect(page.getByRole("form")).toHaveCount(0);
  });

  test("an approval signs the applicant out, and checking the status asks them to sign in again", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "PENDING" });
    await page.goto("/provider");
    await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
    // Let the dashboard finish loading; any request after the decision would end the session by itself.
    await expect(page.getByText("No decisions yet.")).toBeVisible();
    log.review("APPROVED");
    await page.getByRole("button", { name: "Check status" }).click();
    // StateMessage renders its title as text, not a heading.
    await expect(page.getByRole("status").filter({ hasText: "Sign in again to see your application status" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Sign in again" })).toHaveAttribute("href", "/sign-in?redirect=%2Fprovider");
    await expectNoAxeViolations(page);
    await expect(page.getByText("Your application is under review")).toHaveCount(0);
  });

  test("review decisions are readable and dismissable on the onboarding dashboard", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "REJECTED" });
    await page.goto("/provider");
    const updates = page.getByRole("list", { name: "Application updates" });
    await expect(updates.getByText("Your provider application was not approved")).toBeVisible();
    // Only application decisions are listed; the account's other notifications stay out of onboarding.
    await expect(updates.getByText("New appointment request")).toHaveCount(0);
    await expect(page.getByText("No reason is recorded with the decision.", { exact: false })).toBeVisible();
    await expectNoHorizontalScroll(page);
    await expectComfortableTargets(page);
    await expectNoAxeViolations(page);
    await updates.getByRole("button", { name: "Mark as read" }).click();
    await expect(updates.getByText(/· Read$/)).toBeVisible();
    expect(log.requests).toContainEqual(expect.objectContaining({ method: "PATCH", path: expect.stringMatching(/^\/notifications\/n-decision-\d+\/read$/) }));
  });

  test("a suspension during onboarding ends the session gracefully", async ({ page }) => {
    const log = await stubProviderApi(page, { role: "PATIENT", accountType: "PROVIDER", application: "PENDING" });
    await page.goto("/provider");
    await expect(page.getByText("No decisions yet.")).toBeVisible();
    log.review("SUSPENDED");
    await page.getByRole("button", { name: "Check status" }).click();
    await expect(page.getByRole("status").filter({ hasText: "Sign in again to see your application status" })).toBeVisible();
    await expect(page.getByRole("list", { name: "Application updates" })).toHaveCount(0);
  });

  test("an approved provider who signs in again gets the doctor workspace", async ({ page }) => {
    await stubProviderApi(page, { role: "PROVIDER", accountType: "PROVIDER", application: "APPROVED" });
    await page.goto("/provider");
    await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toBeVisible();
    await expect(page.getByRole("list", { name: "Provider sign-up steps" })).toHaveCount(0);
  });
});
