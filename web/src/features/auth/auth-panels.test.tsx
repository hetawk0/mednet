import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccountPanel } from "./account-panel";
import { ProviderSignUpPanel } from "./provider-sign-up-panel";
import { ResetPasswordPanel } from "./reset-password-panel";
import { SignInPanel } from "./sign-in-panel";
import { VerifyEmailPanel } from "./verify-email-panel";

const routerPush = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

type Route = { status: number; body?: unknown };
const routes = new Map<string, Route[]>();
const calls: Array<{ url: string; init?: RequestInit }> = [];

function on(method: string, path: string, ...responses: Route[]) {
  routes.set(`${method} ${path}`, responses);
}

const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = String(input);
  calls.push({ url, init });
  const key = `${init?.method ?? "GET"} ${url.split("?")[0]}`;
  if (key === "GET /api/v1/auth/google/status" && !routes.has(key)) {
    return Response.json({ enabled: false });
  }
  const queue = routes.get(key);
  if (key === "GET /api/v1/auth/csrf") return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" });
  if (!queue?.length) throw new Error(`Unexpected request ${key}`);
  const next = queue.length > 1 ? queue.shift()! : queue[0];
  return next.body === undefined
    ? new Response(null, { status: next.status })
    : Response.json(next.body, { status: next.status });
});

const replace = vi.fn();
const unauthenticated = {
  status: 401,
  body: { success: false, error: { code: "UNAUTHORIZED", message: "Sign-in required" } },
};

beforeEach(() => {
  routes.clear();
  calls.length = 0;
  routerPush.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  replace.mockReset();
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { ...window.location, replace, search: "", origin: "http://localhost:3000" },
  });
});

afterEach(() => vi.unstubAllGlobals());

describe("SignInPanel", () => {
  it("redirects an existing session to the role home", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "a@b.lr", role: "SUPER_ADMIN" } });
    render(<SignInPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/admin"));
  });

  it("shows Google sign-in only when OAuth is configured by the API", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("GET", "/api/v1/auth/google/status", { status: 200, body: { enabled: true } });
    render(<SignInPanel />);
    expect(await screen.findByRole("link", { name: "Continue with Google" }))
      .toHaveAttribute("href", "/oauth2/authorization/google");
  });

  it("sends a provider sign-up awaiting approval to onboarding, using the session's account type", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated, {
      status: 200,
      body: { email: "doctor@b.lr", role: "PATIENT", accountType: "PROVIDER" },
    });
    on("POST", "/api/v1/auth/login", { status: 204 });
    render(<SignInPanel />);
    await userEvent.type(await screen.findByRole("textbox", { name: /Email address/ }), "doctor@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "a-valid-password-123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/provider"));
  });

  it("opens patient sign-up directly from ?mode=register", async () => {
    window.location.search = "?mode=register";
    on("GET", "/api/v1/auth/session", unauthenticated);
    render(<SignInPanel />);
    expect(await screen.findByRole("heading", { name: "Create a patient account" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "sign up as a healthcare provider" })).toHaveAttribute("href", "/sign-up/provider");
  });

  it("validates fields before attempting API authentication", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    render(<SignInPanel />);
    await userEvent.click(await screen.findByRole("button", { name: "Sign in" }));
    const email = screen.getByRole("textbox", { name: /Email address/ });
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email).toHaveFocus();
    expect(calls.some((call) => call.url === "/api/v1/auth/login")).toBe(false);
  });

  it("signs in through MedNet and redirects using the backend role", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated, { status: 200, body: { email: "p@b.lr", role: "PATIENT" } });
    on("POST", "/api/v1/auth/login", { status: 204 });
    render(<SignInPanel />);
    await userEvent.type(await screen.findByRole("textbox", { name: /Email address/ }), "p@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "correct horse battery");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/patient"));
    const loginCall = calls.find((call) => call.url === "/api/v1/auth/login");
    expect(new URLSearchParams(String(loginCall?.init?.body))).toEqual(
      new URLSearchParams({ email: "p@b.lr", password: "correct horse battery" }),
    );
  });

  it("reports a MedNet login service outage without disguising it as bad credentials", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/login", {
      status: 503,
      body: { success: false, error: { code: "SERVICE_UNAVAILABLE", message: "Sign-in unavailable" } },
    });

    render(<SignInPanel />);
    await userEvent.type(await screen.findByRole("textbox", { name: /Email address/ }), "p@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "any-password-123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sign-in is temporarily unavailable. Please try again shortly.",
    );
  });

  it("registers through the MedNet API and opens code entry automatically", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/register", {
      status: 202,
      body: { message: "If a new account can be created for this email, we will send a verification link." },
    });
    render(<SignInPanel />);
    await userEvent.click(await screen.findByText("Create account"));
    await userEvent.type(screen.getByRole("textbox", { name: /Email address/ }), "new@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "a-valid-password-123");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/verify?email=new%40b.lr"));
    const registerCall = calls.find((call) => call.url === "/api/v1/auth/register");
    expect(JSON.parse(String(registerCall?.init?.body))).toEqual({
      email: "new@b.lr",
      password: "a-valid-password-123",
    });
  });

  it("opens the code form after resending verification", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/login", unauthenticated);
    on("POST", "/api/v1/auth/resend-verification", {
      status: 202,
      body: { message: "A six-digit code will be sent shortly." },
    });

    render(<SignInPanel />);
    await userEvent.type(await screen.findByRole("textbox", { name: /Email address/ }), "new@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "a-valid-password-123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await userEvent.click(await screen.findByRole("button", { name: "Resend verification email" }));
    await userEvent.click(screen.getByRole("button", { name: "Resend verification email" }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/verify?email=new%40b.lr"));
  });

  it("shows an actionable message when an account email is already registered", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/register", {
      status: 409,
      body: {
        success: false,
        error: {
          code: "CONFLICT",
          message: "An account with this email already exists. Sign in or reset your password.",
        },
      },
    });
    render(<SignInPanel />);
    await userEvent.click(await screen.findByText("Create account"));
    await userEvent.type(screen.getByRole("textbox", { name: /Email address/ }), "existing@b.lr");
    await userEvent.type(screen.getByLabelText(/^Password/), "a-valid-password-123");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An account with this email already exists.",
    );
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reset password" })).toHaveAttribute(
      "href",
      "/reset?email=existing%40b.lr",
    );
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(screen.getByRole("heading", { name: "Sign in to MedNet" })).toBeInTheDocument();
  });

  it("prepares old accounts for recovery and links to the six-digit code step", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/forgot-password", {
      status: 202,
      body: { message: "If the account exists, password recovery instructions have been sent." },
    });
    render(<SignInPanel />);
    await userEvent.click(await screen.findByRole("button", { name: "Forgot password?" }));
    await userEvent.type(screen.getByRole("textbox", { name: /Email address/ }), "p@b.lr");
    await userEvent.click(screen.getByRole("button", { name: "Send recovery instructions" }));

    expect(await screen.findByRole("status")).toHaveTextContent("password recovery instructions");
    expect(screen.getByRole("link", { name: "Enter your recovery code" })).toHaveAttribute(
      "href",
      "/reset?email=p%40b.lr",
    );
  });
});

describe("ResetPasswordPanel", () => {
  it("verifies the recovery code and resets the password through MedNet", async () => {
    window.history.replaceState = vi.fn();
    on("POST", "/api/v1/auth/reset-password/verify", {
      status: 200,
      body: { token: "reset-ticket", message: "Code verified." },
    });
    on("POST", "/api/v1/auth/reset-password", {
      status: 200,
      body: { message: "Password updated. You can now sign in." },
    });
    render(<ResetPasswordPanel initialEmail="p@b.lr" />);

    expect(screen.getByRole("heading", { name: "Enter your recovery code" })).toBeInTheDocument();
    await userEvent.type(screen.getByRole("textbox", { name: /Six-digit code/ }), "12a3456");
    expect(screen.getByRole("textbox", { name: /Six-digit code/ })).toHaveValue("123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify code" }));

    expect(await screen.findByRole("heading", { name: "Choose a new password" })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^New password/), "a-brand-new-password");
    await userEvent.click(screen.getByRole("button", { name: "Update password" }));
    expect(await screen.findByText("Password updated. You can now sign in.")).toBeInTheDocument();
    const resetCall = calls.find((call) => call.url === "/api/v1/auth/reset-password");
    expect(JSON.parse(String(resetCall?.init?.body))).toEqual({
      email: "p@b.lr",
      token: "reset-ticket",
      password: "a-brand-new-password",
    });
  });

  it("shows an invalid-code error returned by MedNet", async () => {
    on("POST", "/api/v1/auth/reset-password/verify", {
      status: 400,
      body: {
        success: false,
        error: { code: "BAD_REQUEST", message: "The code is invalid or expired. Request a new code." },
      },
    });
    render(<ResetPasswordPanel initialEmail="p@b.lr" />);
    await userEvent.type(screen.getByRole("textbox", { name: /Six-digit code/ }), "000000");
    await userEvent.click(screen.getByRole("button", { name: "Verify code" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("The code is invalid or expired. Request a new code.");
  });
});

describe("VerifyEmailPanel", () => {
  it("shows and focuses the six-digit code field immediately, then verifies through MedNet", async () => {
    on("POST", "/api/v1/auth/verify-code", {
      status: 200,
      body: { message: "Email verified. You can now sign in." },
    });
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...window.location, search: "?email=p%40b.lr", origin: "http://localhost:3000" },
    });
    render(<VerifyEmailPanel />);
    const codeInput = await screen.findByRole("textbox", { name: /Six-digit verification code/ });
    expect(codeInput).toHaveFocus();
    await userEvent.type(codeInput, "123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify email" }));

    expect(
      await screen.findByText("Your email address is confirmed. Sign in to continue; provider accounts continue with their application."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Continue to sign in" })).toHaveAttribute("href", "/sign-in");
    const verifyCall = calls.find((call) => call.url === "/api/v1/auth/verify-code");
    expect(JSON.parse(String(verifyCall?.init?.body))).toEqual({ email: "p@b.lr", code: "123456" });
  });

  it("resends a fresh verification code and secure link from the code-entry page", async () => {
    on("POST", "/api/v1/auth/resend-verification", {
      status: 202,
      body: {
        message:
          "If an account with this email needs verification, we will send a new six-digit code and secure verification link shortly.",
      },
    });
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...window.location, search: "?email=p%40b.lr", origin: "http://localhost:3000" },
    });
    render(<VerifyEmailPanel />);
    await userEvent.click(await screen.findByRole("button", { name: "Resend code and secure link" }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "new six-digit code and secure verification link",
    );
    const resendCall = calls.find((call) => call.url === "/api/v1/auth/resend-verification");
    expect(JSON.parse(String(resendCall?.init?.body))).toEqual({ email: "p@b.lr" });
  });

  it("keeps the one-time verification link working", async () => {
    on("GET", "/api/v1/auth/verify", {
      status: 200,
      body: { message: "Email verified. You can now sign in." },
    });
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        ...window.location,
        search: "?email=p%40b.lr&token=single-use-token",
        origin: "http://localhost:3000",
      },
    });
    render(<VerifyEmailPanel />);

    expect(
      await screen.findByText("Your email address is confirmed. Sign in to continue; provider accounts continue with their application."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Continue to sign in" })).toHaveAttribute("href", "/sign-in");
    expect(calls.some((call) => call.url.startsWith("/api/v1/auth/verify?email=p%40b.lr"))).toBe(true);
  });
});

describe("AccountPanel", () => {
  it("forwards administrators to /admin without rendering account details", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "admin@b.lr", role: "ADMIN" } });
    render(<AccountPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/admin"));
    expect(screen.queryByText("admin@b.lr")).not.toBeInTheDocument();
  });

  it("forwards providers to their workspace", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "doctor@b.lr", role: "PROVIDER" } });
    render(<AccountPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/provider"));
    expect(screen.queryByText("doctor@b.lr")).not.toBeInTheDocument();
  });

  it("forwards laboratory staff to the laboratory workspace", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "lab@b.lr", role: "LABORATORY" } });
    render(<AccountPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/laboratory"));
    expect(screen.queryByText("lab@b.lr")).not.toBeInTheDocument();
  });

  it("forwards home-care staff to the home-care workspace", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "care@b.lr", role: "HOME_CARE" } });
    render(<AccountPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/home-care"));
    expect(screen.queryByText("care@b.lr")).not.toBeInTheDocument();
  });

  it("shows the session email and signs out through MedNet", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "patient@b.lr", role: "PATIENT" } });
    on("POST", "/api/v1/auth/logout", { status: 204 });
    render(<AccountPanel />);
    expect(await screen.findByText("patient@b.lr")).toBeInTheDocument();
    expect(screen.getByText("Patient")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("link", { name: "Go to sign in" })).toHaveAttribute("href", "/sign-in");
    expect(calls.some((call) => call.url === "/api/v1/auth/logout")).toBe(true);
  });

  it("keeps the session and explains a failed sign-out", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "p@b.lr", role: "PATIENT" } });
    on("POST", "/api/v1/auth/logout", { status: 500 });
    render(<AccountPanel />);
    await userEvent.click(await screen.findByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Sign-out failed. Close this browser session and try again.");
    expect(screen.getByText("p@b.lr")).toBeInTheDocument();
  });

  it("reports an unreachable API instead of claiming the visitor is signed out silently", async () => {
    on("GET", "/api/v1/auth/session", { status: 502 });
    render(<AccountPanel />);
    expect(await screen.findByRole("alert")).toHaveTextContent("The MedNet API is unavailable. Try again shortly.");
    expect(screen.getByRole("link", { name: "Go to sign in" })).toBeInTheDocument();
  });
});

describe("ProviderSignUpPanel", () => {
  async function fillAndSubmit(email = "doctor@b.lr", password = "a-valid-password-123") {
    if (email) await userEvent.type(await screen.findByRole("textbox", { name: /Professional email address/ }), email);
    if (password) await userEvent.type(screen.getByLabelText(/^Password/), password);
    await userEvent.click(screen.getByRole("button", { name: "Create provider account" }));
  }

  it("registers a PROVIDER account through the same endpoint and opens email verification", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/register", { status: 202, body: { message: "Check your email." } });
    render(<ProviderSignUpPanel />);
    await fillAndSubmit();

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/verify?email=doctor%40b.lr"));
    const registerCall = calls.find((call) => call.url === "/api/v1/auth/register");
    expect(JSON.parse(String(registerCall?.init?.body))).toEqual({
      email: "doctor@b.lr",
      password: "a-valid-password-123",
      accountType: "PROVIDER",
    });
  });

  it("validates the email and password before calling the API", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    render(<ProviderSignUpPanel />);
    await screen.findByRole("textbox", { name: /Professional email address/ });
    await fillAndSubmit("", "short");
    expect(screen.getByRole("textbox", { name: /Professional email address/ })).toHaveFocus();
    expect(calls.some((call) => call.url === "/api/v1/auth/register")).toBe(false);
  });

  it("sends an existing account back to sign-in to continue its application instead of registering again", async () => {
    on("GET", "/api/v1/auth/session", unauthenticated);
    on("POST", "/api/v1/auth/register", {
      status: 409,
      body: { success: false, error: { code: "CONFLICT", message: "An account with this email already exists." } },
    });
    render(<ProviderSignUpPanel />);
    await fillAndSubmit("existing@b.lr");

    expect(await screen.findByRole("alert")).toHaveTextContent("An account with this email already exists.");
    expect(screen.getByRole("link", { name: "Sign in to continue" })).toHaveAttribute("href", "/sign-in?redirect=%2Fprovider");
    expect(screen.getByRole("link", { name: "Reset password" })).toHaveAttribute("href", "/reset?email=existing%40b.lr");
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("continues an already signed-in applicant's onboarding instead of offering a second account", async () => {
    on("GET", "/api/v1/auth/session", { status: 200, body: { email: "doctor@b.lr", role: "PATIENT", accountType: "PROVIDER" } });
    render(<ProviderSignUpPanel />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/provider"));
  });
});
