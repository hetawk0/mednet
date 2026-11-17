import AxeBuilder from "@axe-core/playwright";
import { expect, request, test, type APIRequestContext, type Page } from "@playwright/test";
import { canDeliverEmailTokens, deliverVerificationToken, queryRows, sqlLiteral } from "./support/live-backend";
import { adminContext, createVerifiedAccount, signedInContext } from "./support/seed-directory";

/**
 * Provider sign-up and onboarding against a running MedNet API (no mocks). Run with MEDNET_LIVE_API=1
 * and a disposable database (see e2e/README.md); email delivery stays off, so the verification link is
 * supplied by `support/live-backend.ts` and notification emails never leave the outbox.
 *
 * Each viewport project registers its own applicant through /sign-up/provider and follows the account
 * through verification, application, rejection, resubmission, approval and suspension. Every review
 * decision is checked against the real session store: the applicant's previous session cookie must stop
 * working, and the next sign-in must carry the new role. Provider-only clinical endpoints are probed with
 * the applicant's real cookies at every stage.
 */
test.describe.configure({ mode: "serial" });

const password = "Onboard-Live-Pass-1";
let baseURL = "";
let tag = "";
let applicant = { email: "", displayName: "", accountId: "" };
const bystander = { email: "", accountId: "", context: null as unknown as APIRequestContext };
let admin: APIRequestContext;
let applicationId = "";

async function csrf(context: APIRequestContext) {
  const token = await (await context.get("/api/v1/auth/csrf")).json();
  return { [token.headerName]: token.token } as Record<string, string>;
}

async function call(context: APIRequestContext, method: "get" | "post" | "patch", url: string, data?: unknown) {
  const response = method === "get" ? await context.get(url) : await context[method](url, { data, headers: await csrf(context) });
  if (!response.ok()) throw new Error(`${method.toUpperCase()} ${url} → ${response.status()} ${await response.text()}`);
  const text = await response.text();
  return text ? JSON.parse(text) : undefined;
}

async function review(status: "APPROVED" | "REJECTED" | "SUSPENDED") {
  const result = await call(admin, "patch", `/api/v1/admin/providers/${applicationId}/status`, { status });
  expect(result.status).toBe(status);
}

async function signIn(page: Page) {
  await page.context().clearCookies();
  await page.goto("/sign-in");
  await page.getByRole("textbox", { name: "Email address" }).fill(applicant.email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

/** A separate API client holding a copy of the page's current session cookie. */
async function sessionSnapshot(page: Page) {
  return request.newContext({ baseURL, storageState: await page.context().storageState() });
}

async function sessionOf(context: APIRequestContext) {
  const response = await context.get("/api/v1/auth/session");
  return response.ok() ? await response.json() : response.status();
}

/** Provider-only endpoints; a PATIENT-role session must get 403 from each. */
async function clinicalStatuses(context: APIRequestContext) {
  const headers = await csrf(context);
  const startsAt = new Date(Date.now() + 72 * 3_600_000);
  startsAt.setUTCSeconds(0, 0);
  return {
    patients: (await context.get("/api/v1/providers/me/patients")).status(),
    labQueue: (await context.get("/api/v1/providers/me/lab-review-queue")).status(),
    records: (await context.get(`/api/v1/patients/${bystander.accountId}/records`)).status(),
    vitals: (await context.get(`/api/v1/patients/${bystander.accountId}/vitals`)).status(),
    publishAvailability: (
      await context.post("/api/v1/providers/me/availability", {
        data: { startsAt: startsAt.toISOString(), endsAt: new Date(startsAt.getTime() + 1_800_000).toISOString(), consultationMode: "TEXT" },
        headers,
      })
    ).status(),
  };
}

const allForbidden = { patients: 403, labQueue: 403, records: 403, vitals: 403, publishAvailability: 403 };

async function inDirectory() {
  const anonymous = await request.newContext({ baseURL });
  const page = await (await anonymous.get(`/api/v1/providers?search=${encodeURIComponent(applicant.displayName)}`)).json();
  await anonymous.dispose();
  return (page.content as Array<{ displayName: string }>).some((entry) => entry.displayName === applicant.displayName);
}

async function decisionNotices(context: APIRequestContext) {
  const page = await call(context, "get", "/api/v1/notifications?eventType=PROVIDER_APPLICATION&size=50");
  return page.content as Array<{ id: string; title: string; readAt: string | null }>;
}

async function expectAccessible(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).exclude("nextjs-portal").analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes[0]?.target.join(" ")}`)).toEqual([]);
}

test.beforeAll(async ({}, testInfo) => {
  baseURL = String(testInfo.project.use.baseURL);
  tag = `${testInfo.project.name}${Date.now().toString(36)}`;
  applicant = { email: `onboard-${tag}@mednet.test`, displayName: `Dr. Onboard ${tag}`, accountId: "" };
  admin = await adminContext(baseURL);
  // An unrelated patient: the target of clinical probes and the control for notification scoping.
  bystander.email = `onboard-bystander-${tag}@mednet.test`;
  bystander.accountId = (await createVerifiedAccount(admin, { email: bystander.email, displayName: `Bystander ${tag}`, accountType: "PATIENT", password })).id;
  bystander.context = await signedInContext(baseURL, bystander.email, password);
});

test.afterAll(async () => {
  await bystander.context?.dispose();
  await admin?.dispose();
});

test("1. /sign-up/provider registers a PROVIDER account that cannot sign in before verification", async ({ page }) => {
  await page.goto("/sign-up/provider");
  await expect(page.getByRole("heading", { name: "Create your provider account" })).toBeVisible();
  await page.getByRole("textbox", { name: "Professional email address" }).fill(applicant.email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(password);
  const response = page.waitForResponse("**/api/v1/auth/register");
  await page.getByRole("button", { name: "Create provider account" }).click();
  const registered = await response;
  expect(registered.request().postDataJSON()).toEqual({ email: applicant.email, password, accountType: "PROVIDER" });
  if (registered.status() === 503) {
    // Email delivery is off in these runs: the account exists, the verification email was not sent.
    await expect(page.getByRole("form", { name: "Create your provider account" }).getByRole("alert")).toBeVisible();
    await expect(page).toHaveURL(/\/sign-up\/provider$/);
  } else {
    expect(registered.status()).toBe(202);
    await expect(page).toHaveURL(new RegExp(`/verify\\?email=${encodeURIComponent(applicant.email).replace(/[.]/g, "\\.")}$`));
  }

  const accounts = await call(admin, "get", `/api/v1/admin/accounts?search=${encodeURIComponent(applicant.email)}`);
  const account = (accounts.content as Array<Record<string, unknown>>).find((a) => a.email === applicant.email);
  expect(account).toMatchObject({ accountType: "PROVIDER", emailVerified: false });
  applicant.accountId = String(account!.id);

  // A second registration with the same address is refused rather than overwriting the account.
  const again = await request.newContext({ baseURL });
  const duplicate = await again.post("/api/v1/auth/register", {
    data: { email: applicant.email, password, accountType: "PROVIDER" },
    headers: await csrf(again),
  });
  expect(duplicate.status()).toBe(409);
  // Unverified accounts cannot sign in.
  const login = await again.post("/api/v1/auth/login", { form: { email: applicant.email, password }, headers: await csrf(again) });
  expect(login.status()).toBe(401);
  await again.dispose();
});

test("2. the emailed link verifies the account; sign-in opens onboarding with no provider access", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a disposable database (MEDNET_LIVE_H2_URL or MEDNET_LIVE_PG_URL)");
  const token = deliverVerificationToken(applicant.email);
  await page.goto(`/verify?email=${encodeURIComponent(applicant.email)}&token=${token}`);
  await expect(page.getByRole("status").filter({ hasText: "Your email address is confirmed." })).toBeVisible();

  await signIn(page);
  await expect(page).toHaveURL(/\/provider$/);
  await expect(page.getByRole("heading", { name: "Submit your professional details" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toHaveCount(0);
  expect(await sessionOf(page.request)).toEqual({ email: applicant.email, role: "PATIENT", accountType: "PROVIDER" });
  expect((await page.request.get("/api/v1/providers/me/application")).status()).toBe(404);
  expect(await clinicalStatuses(page.request)).toEqual(allForbidden);

  // Option A: the web client keeps applicants out of the patient workspace.
  await page.goto("/patient");
  await expect(page).toHaveURL(/\/provider$/);
  await expectAccessible(page);
});

test("3. the applicant submits professional details; the application is pending and unlisted", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified applicant");
  await signIn(page);
  const form = page.getByRole("form", { name: "Professional details" });
  await form.getByLabel(/^Display name/).fill(applicant.displayName);
  await form.getByLabel(/^Specialty/).fill("Family medicine");
  await form.getByLabel(/^Licence or registration reference/).fill(`LIVE-${tag}`);
  await form.getByRole("button", { name: "Submit application" }).click();
  await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  await expect(page.getByText("No decisions yet.")).toBeVisible();

  const application = await call(page.request, "get", "/api/v1/providers/me/application");
  expect(application).toMatchObject({ status: "PENDING", displayName: applicant.displayName, credentialReference: `LIVE-${tag}` });
  applicationId = application.id;
  const pending = (await call(admin, "get", "/api/v1/admin/providers?status=PENDING")) as Array<{ id: string }>;
  expect(pending.map((p) => p.id)).toContain(applicationId);

  const resubmit = await page.request.post("/api/v1/providers/applications", {
    data: { displayName: applicant.displayName, specialty: "Family medicine", credentialReference: "LIVE-DUPLICATE" },
    headers: await csrf(page.request),
  });
  expect(resubmit.status()).toBe(409);
  expect(await clinicalStatuses(page.request)).toEqual(allForbidden);
  expect(await inDirectory()).toBe(false);
  await expectAccessible(page);
});

test("4. rejection ends the session; the applicant reads the decision and resubmits", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified applicant");
  await signIn(page);
  await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  await expect(page.getByText("No decisions yet.")).toBeVisible();
  const before = await sessionSnapshot(page);

  await review("REJECTED");
  expect(await sessionOf(before)).toBe(401);
  await before.dispose();
  await page.getByRole("button", { name: "Check status" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Sign in again to see your application status" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in again" })).toHaveAttribute("href", "/sign-in?redirect=%2Fprovider");

  await signIn(page);
  await expect(page.getByRole("heading", { name: "Your application was not approved" })).toBeVisible();
  expect(await sessionOf(page.request)).toMatchObject({ role: "PATIENT", accountType: "PROVIDER" });
  expect(await clinicalStatuses(page.request)).toEqual(allForbidden);
  const updates = page.getByRole("list", { name: "Application updates" });
  await expect(updates.getByText("Your provider application was not approved")).toBeVisible();
  await expectAccessible(page);
  await updates.getByRole("button", { name: "Mark as read" }).click();
  await expect(updates.getByText(/· Read$/)).toBeVisible();

  const form = page.getByRole("form", { name: "Submit corrected details" });
  await form.getByLabel(/^Display name/).fill(applicant.displayName);
  await form.getByLabel(/^Specialty/).fill("Family medicine");
  await form.getByLabel(/^Licence or registration reference/).fill(`LIVE-${tag}-2`);
  await form.getByRole("button", { name: "Submit corrected application" }).click();
  await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  const corrected = await call(page.request, "get", "/api/v1/providers/me/application");
  expect(corrected).toMatchObject({ status: "PENDING", credentialReference: `LIVE-${tag}-2` });
  applicationId = corrected.id;
  expect(await clinicalStatuses(page.request)).toEqual(allForbidden);
});

test("5. decision notices reach only the applicant", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified applicant");
  await signIn(page);
  await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  const mine = await decisionNotices(page.request);
  expect(mine.map((n) => n.title)).toEqual(["Your provider application was not approved"]);
  expect(mine[0].readAt).not.toBeNull();

  // Another account sees none of them and cannot read or mark them.
  expect(await decisionNotices(bystander.context)).toEqual([]);
  const foreign = await bystander.context.patch(`/api/v1/notifications/${mine[0].id}/read`, { headers: await csrf(bystander.context) });
  expect(foreign.status()).toBe(404);
  // The filter is validated rather than passed through.
  expect((await page.request.get("/api/v1/notifications?eventType=provider_application")).status()).toBe(400);
});

test("6. approval ends the session; the next sign-in is a PROVIDER with the doctor workspace", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified applicant");
  await signIn(page);
  await expect(page.getByRole("heading", { name: "Your application is under review" })).toBeVisible();
  await expect(page.getByText("No decisions yet.").or(page.getByRole("list", { name: "Application updates" }))).toBeVisible();
  const before = await sessionSnapshot(page);

  await review("APPROVED");
  expect(await sessionOf(before)).toBe(401);
  // The old cookie gains nothing from the approval.
  expect((await before.get("/api/v1/providers/me/patients")).status()).toBe(401);
  await before.dispose();

  await signIn(page);
  await expect(page).toHaveURL(/\/provider$/);
  await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toBeVisible();
  await expect(page.getByRole("list", { name: "Provider sign-up steps" })).toHaveCount(0);
  expect(await sessionOf(page.request)).toEqual({ email: applicant.email, role: "PROVIDER", accountType: "PROVIDER" });
  expect((await page.request.get("/api/v1/providers/me/patients")).status()).toBe(200);
  expect((await page.request.get("/api/v1/providers/me/lab-review-queue")).status()).toBe(200);
  // No care relationship and no consent: an unrelated patient's clinical data stays closed.
  expect((await page.request.get(`/api/v1/patients/${bystander.accountId}/records`)).status()).toBe(403);
  expect(await inDirectory()).toBe(true);
  const titles = (await decisionNotices(page.request)).map((n) => n.title);
  expect(titles).toContain("Your provider application was approved");
  expect(await decisionNotices(bystander.context)).toEqual([]);
});

test("7. suspension ends the session and removes provider access", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified applicant");
  await signIn(page);
  await expect(page.getByRole("navigation", { name: "Doctor workspace" })).toBeVisible();
  const before = await sessionSnapshot(page);

  await review("SUSPENDED");
  expect(await sessionOf(before)).toBe(401);
  expect((await before.get("/api/v1/providers/me/patients")).status()).toBe(401);
  await before.dispose();

  await signIn(page);
  await expect(page.getByRole("heading", { name: "Your provider listing is suspended" })).toBeVisible();
  await expect(page.getByRole("form")).toHaveCount(0);
  expect(await sessionOf(page.request)).toMatchObject({ role: "PATIENT", accountType: "PROVIDER" });
  expect(await clinicalStatuses(page.request)).toEqual(allForbidden);
  const resubmit = await page.request.post("/api/v1/providers/applications", {
    data: { displayName: applicant.displayName, specialty: "Family medicine", credentialReference: "LIVE-WHILE-SUSPENDED" },
    headers: await csrf(page.request),
  });
  expect(resubmit.status()).toBe(409);
  expect(await inDirectory()).toBe(false);
  await expectAccessible(page);
});

test("8. queued notification emails are addressed only to the applicant and none were sent", async () => {
  test.skip(!canDeliverEmailTokens, "needs read access to the disposable database");
  const rows = queryRows(
    "SELECT o.recipient_account_id, o.status, n.recipient_account_id FROM notification_email_outbox o " +
      "JOIN user_notifications n ON n.id = o.notification_id " +
      `WHERE n.event_type = 'PROVIDER_APPLICATION' AND n.recipient_account_id IN (${sqlLiteral(applicant.accountId)}, ${sqlLiteral(bystander.accountId)})`,
  );
  // Rejection and approval each queue one email when delivery is enabled; with it disabled, none.
  expect([0, 2]).toContain(rows.length);
  for (const [outboxRecipient, status, notificationRecipient] of rows) {
    expect(outboxRecipient).toBe(applicant.accountId);
    expect(notificationRecipient).toBe(applicant.accountId);
    // EKDSend has no key in these runs, so nothing may reach SENT.
    expect(status).not.toBe("SENT");
  }
});
