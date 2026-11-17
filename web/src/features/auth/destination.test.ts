import { describe, expect, it } from "vitest";
import { emailProblem, passwordProblem } from "./auth-api";
import { destinationAfterSignIn, safeDestination } from "./destination";

const origin = "http://localhost:3000";

describe("safeDestination", () => {
  it.each([
    [null, "/patient"],
    ["", "/patient"],
    ["https://evil.example/admin", "/patient"],
    ["//evil.example/admin", "/patient"],
    ["/\\evil.example", "/patient"],
    ["/sign-in", "/patient"],
    ["/sign-in?redirect=/admin", "/patient"],
    ["/admin", "/admin"],
    ["/patient/profile?tab=1#top", "/patient/profile?tab=1#top"],
  ])("redirect %j → %s", (redirect, expected) => {
    expect(safeDestination(redirect, "/patient", origin)).toBe(expected);
  });
});

describe("destinationAfterSignIn", () => {
  it("uses the role home when there is no redirect", () => {
    expect(destinationAfterSignIn("ADMIN", "", origin)).toBe("/admin");
    expect(destinationAfterSignIn("SUPER_ADMIN", "", origin)).toBe("/admin");
    expect(destinationAfterSignIn("PATIENT", "", origin)).toBe("/patient");
    expect(destinationAfterSignIn("PROVIDER", "", origin)).toBe("/provider");
    expect(destinationAfterSignIn("LABORATORY", "", origin)).toBe("/laboratory");
    expect(destinationAfterSignIn("HOME_CARE", "", origin)).toBe("/home-care");
  });

  it("sends provider sign-ups awaiting approval to onboarding, and approved providers to their workspace", () => {
    expect(destinationAfterSignIn("PATIENT", "", origin, "PROVIDER")).toBe("/provider");
    expect(destinationAfterSignIn("PATIENT", "", origin, "PATIENT")).toBe("/patient");
    expect(destinationAfterSignIn("PATIENT", "", origin, null)).toBe("/patient");
    expect(destinationAfterSignIn("PROVIDER", "", origin, "PROVIDER")).toBe("/provider");
    // The account type never upgrades access: an admin stays an admin, whatever is stored.
    expect(destinationAfterSignIn("ADMIN", "", origin, "PROVIDER")).toBe("/admin");
  });

  it("honours a safe redirect", () => {
    expect(destinationAfterSignIn("ADMIN", "?redirect=%2Fadmin", origin)).toBe("/admin");
    expect(destinationAfterSignIn("PATIENT", "?redirect=%2F%2Fevil.example", origin)).toBe("/patient");
  });
});

describe("client-side validation mirrors the backend limits", () => {
  it("checks passwords: 12 characters minimum, 72 UTF-8 bytes maximum", () => {
    expect(passwordProblem("short")).toMatch(/12 characters/);
    expect(passwordProblem("a".repeat(12))).toBeNull();
    expect(passwordProblem("é".repeat(37))).toMatch(/72 bytes/);
  });

  it("checks email format and length", () => {
    expect(emailProblem("")).toMatch(/Enter your email/);
    expect(emailProblem("not-an-email")).toMatch(/valid email/);
    expect(emailProblem("patient@example.lr")).toBeNull();
  });
});
