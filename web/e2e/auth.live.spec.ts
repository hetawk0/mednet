import { expect, test, type Page } from "@playwright/test";
import {
  canDeliverEmailTokens,
  deliverResetCode,
  deliverVerificationToken,
  liveAdmin,
} from "./support/live-backend";

/**
 * Account access against a running MedNet API (no mocks). Run with MEDNET_LIVE_API=1; see
 * e2e/README.md. Each viewport project uses its own patient account, so the three widths run
 * the whole journey independently. Email delivery is off, so registration reports the API's
 * delivery failure and the emailed token is supplied by `support/live-backend.ts`.
 */
test.describe.configure({ mode: "serial" });

const password = "Phase4a-Patient-Pass-1";
const newPassword = "Phase4a-Patient-Pass-2";

let patientEmail = "";

test.beforeAll(({}, testInfo) => {
  patientEmail = `phase4a-${testInfo.project.name}-${Date.now()}@mednet.test`;
});

async function fillSignIn(page: Page, email: string, secret: string) {
  await page.getByLabel("Email address").fill(email);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill(secret);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

test("anonymous visitors see the signed-out account state", async ({ page }) => {
  await page.goto("/account");
  await expect(page.getByRole("heading", { name: "Sign in to continue" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Go to sign in" })).toHaveAttribute("href", "/sign-in");
});

test("Google sign-in reports the deployment status from the API", async ({ page }) => {
  const status = await page.request.get("/api/v1/auth/google/status").then((r) => r.json());
  await page.goto("/sign-in");
  if (status.enabled) await expect(page.getByRole("link", { name: "Continue with Google" })).toBeVisible();
  else {
    // Without Google configured, sign-in offers email and password only (no Google link or notice).
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Continue with Google" })).toHaveCount(0);
  }
});

test("registration creates the account and surfaces the API's email-delivery result", async ({ page }) => {
  await page.goto("/sign-in");
  // The radio is visually hidden; people press its visible label.
  await page.locator("label").filter({ hasText: "Create account" }).click();
  await expect(page.getByRole("radio", { name: "Create account" })).toBeChecked();
  await page.getByLabel("Email address").fill(patientEmail);
  await page.getByRole("textbox", { name: "Password", exact: true }).fill("short");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByText("Use at least 12 characters.")).toBeVisible();

  await page.getByRole("textbox", { name: "Password", exact: true }).fill(password);
  const response = page.waitForResponse("**/api/v1/auth/register");
  await page.getByRole("button", { name: "Create account" }).click();
  const status = (await response).status();
  if (status === 503) {
    await expect(page.getByRole("alert").filter({ hasText: "Verification email could not be sent" })).toBeVisible();
  } else {
    expect(status).toBe(202);
    await expect(page.getByRole("status").filter({ hasText: "Thanks for signing up" })).toBeVisible();
  }
  await expectNoHorizontalScroll(page);
});

test("an unverified account cannot sign in and is offered a new verification email", async ({ page }) => {
  await page.goto("/sign-in");
  await fillSignIn(page, patientEmail, password);
  await expect(page.getByRole("alert").filter({ hasText: "Sign-in failed. Verify your email and password." })).toBeVisible();
  await page.getByRole("button", { name: "Resend verification email" }).click();
  await expect(page.getByRole("heading", { name: "Resend verification email" })).toBeVisible();
  await expect(page.getByLabel("Email address")).toHaveValue(patientEmail);
  await expect(page.getByRole("textbox", { name: "Password", exact: true })).toHaveCount(0);
});

test("a broken or forged verification link is rejected by the API", async ({ page }) => {
  // Without a link token the page offers the six-digit code form instead.
  await page.goto("/verify");
  await expect(page.getByRole("form", { name: "Verify your email" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Six-digit verification code" })).toBeVisible();

  await page.goto(`/verify?email=${encodeURIComponent(patientEmail)}&token=${"A".repeat(43)}`);
  await expect(page.getByRole("alert").filter({ hasText: "Verification link is invalid or expired" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Email address" })).toHaveValue(patientEmail);
  await expect(page.getByRole("status").filter({ hasText: "Email verified" })).toHaveCount(0);
});

test("the emailed verification link verifies the account", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a disposable database (MEDNET_LIVE_H2_URL or MEDNET_LIVE_PG_URL)");
  const token = deliverVerificationToken(patientEmail);
  await page.goto(`/verify?email=${encodeURIComponent(patientEmail)}&token=${token}`);
  await expect(page.getByRole("status").filter({ hasText: "Your email address is confirmed." })).toBeVisible();
  await page.getByRole("link", { name: "Continue to sign in" }).click();
  await expect(page).toHaveURL(/\/sign-in$/);
});

test("a patient signs in, honours a safe redirect, and is kept off the sign-in page", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified patient");
  await page.goto("/sign-in");
  await fillSignIn(page, patientEmail, "Wrong-password-123");
  await expect(page.getByRole("alert").filter({ hasText: "Sign-in failed" })).toBeVisible();

  await page.goto("/sign-in?redirect=%2Fpatient%2Fprofile");
  await fillSignIn(page, patientEmail, password);
  await expect(page).toHaveURL(/\/patient\/profile$/);

  const session = await page.request.get("/api/v1/auth/session").then((r) => r.json());
  expect(session).toEqual({ email: patientEmail, role: "PATIENT", accountType: "PATIENT" });

  await page.goto("/sign-in?redirect=%2F%2Fevil.example");
  await expect(page).toHaveURL(/\/patient$/);
});

test("the account page shows the session without clinical data and signs out", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified patient");
  await page.goto("/sign-in");
  await fillSignIn(page, patientEmail, password);
  await expect(page).toHaveURL(/\/patient$/);

  await page.goto("/account");
  await expect(page.getByRole("heading", { name: "Signed in" })).toBeVisible();
  await expect(page.getByText(patientEmail)).toBeVisible();
  await expect(page.getByText("Patient", { exact: true })).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("heading", { name: "Sign in to continue" })).toBeVisible();
  expect((await page.request.get("/api/v1/auth/session")).status()).toBe(401);
});

test("password reset by emailed code: wrong code, right code, new password", async ({ page }) => {
  test.skip(!canDeliverEmailTokens, "needs a verified patient");
  await page.goto("/sign-in");
  await page.getByRole("button", { name: "Forgot password?" }).click();
  await page.getByLabel("Email address").fill(patientEmail);
  await page.getByRole("button", { name: "Send recovery instructions" }).click();
  await expect(page.getByRole("status").filter({ hasText: "password reset instructions" })).toBeVisible();
  await page.getByRole("link", { name: "Enter your recovery code" }).click();
  await expect(page).toHaveURL(/\/reset\?email=/);

  deliverResetCode(patientEmail, "246810");
  await page.getByLabel("Six-digit code").fill("135791");
  await page.getByRole("button", { name: "Verify code" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "The code is invalid or expired. Request a new code." })).toBeVisible();

  await page.getByLabel("Six-digit code").fill("246810");
  await page.getByRole("button", { name: "Verify code" }).click();
  await expect(page.getByRole("heading", { name: "Choose a new password" })).toBeVisible();
  await expect(page).toHaveURL(/\/reset$/);

  await page.getByRole("textbox", { name: "New password", exact: true }).fill(newPassword);
  await page.getByRole("button", { name: "Update password" }).click();
  await expect(page.getByRole("heading", { name: "Password updated" }).first()).toBeVisible();

  await page.getByRole("link", { name: "Continue to sign in" }).click();
  await fillSignIn(page, patientEmail, password);
  await expect(page.getByRole("alert").filter({ hasText: "Sign-in failed" })).toBeVisible();
  await fillSignIn(page, patientEmail, newPassword);
  await expect(page).toHaveURL(/\/patient$/);
});

test("the super administrator lands on /admin and /account forwards there", async ({ page }) => {
  test.skip(!liveAdmin.email || !liveAdmin.password, "set MEDNET_LIVE_ADMIN_EMAIL and MEDNET_LIVE_ADMIN_PASSWORD");
  // Runs in every viewport project against one API process: the configured super admin can sign in
  // repeatedly since the Phase 4E fix (a new UserDetails per lookup).
  await page.goto("/sign-in");
  await fillSignIn(page, liveAdmin.email, liveAdmin.password);
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto("/account");
  await expect(page).toHaveURL(/\/admin$/);
});
