"use client";

import { type FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { PasswordInput } from "@/components/password-input";

type GoogleStatus = { enabled: boolean };
type AccountSession = { email: string; role: string };
type CsrfResponse = { headerName: string; token: string };

function destinationForRole(role: string) {
  if (role === "ADMIN" || role === "SUPER_ADMIN") return "/admin";
  if (role === "PATIENT") return "/patient";
  return "/account";
}

function safeDestination(redirect: string | null, fallback: string) {
  if (
    !redirect?.startsWith("/") ||
    redirect.startsWith("//") ||
    redirect.includes("\\")
  ) {
    return fallback;
  }
  let requested: URL;
  try {
    requested = new URL(redirect, window.location.origin);
  } catch {
    return fallback;
  }
  if (
    requested.origin !== window.location.origin ||
    requested.pathname === "/sign-in"
  ) {
    return fallback;
  }
  return `${requested.pathname}${requested.search}${requested.hash}`;
}

async function responseError(response: Response, fallback: string) {
  try {
    const payload = (await response.json()) as {
      detail?: string;
      message?: string;
      error?: { message?: string };
    };
    return payload.detail ?? payload.message ?? payload.error?.message ?? fallback;
  } catch {
    return fallback;
  }
}

export function GoogleSignIn({ googleError }: { googleError: boolean }) {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [sessionChecked, setSessionChecked] = useState(false);
  const [mode, setMode] = useState<
    "login" | "register" | "forgot" | "resend"
  >("login");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [emailInput, setEmailInput] = useState("");

  useEffect(() => {
    let active = true;
    async function redirectExistingSession() {
      try {
        const response = await fetch("/api/v1/auth/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (!response.ok) {
          setSessionChecked(true);
          return;
        }
        const session = (await response.json()) as AccountSession;
        const redirect = new URLSearchParams(window.location.search).get(
          "redirect",
        );
        const destination = safeDestination(
          redirect,
          destinationForRole(session.role),
        );
        window.location.replace(destination);
      } catch {
        if (active) setSessionChecked(true);
      }
    }

    void redirectExistingSession();

    async function checkGoogleStatus() {
      try {
        const response = await fetch("/api/v1/auth/google/status", {
          cache: "no-store",
        });
        const status = (await response.json()) as GoogleStatus;
        if (active) setEnabled(response.ok && status.enabled);
      } catch {
        if (active) setEnabled(false);
      }
    }
    void checkGoogleStatus();
    return () => {
      active = false;
    };
  }, []);

  if (!sessionChecked) {
    return (
      <main className="admin-shell admin-auth-checking">
        <p role="status">Checking your session...</p>
      </main>
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        credentials: "same-origin",
      });
      if (!csrfResponse.ok) throw new Error("Could not prepare your request.");
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      if (mode === "login") {
        const response = await fetch("/api/v1/auth/login", {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            [csrf.headerName]: csrf.token,
          },
          body: new URLSearchParams({ email, password }),
        });
        if (!response.ok)
          throw new Error("Sign-in failed. Verify your email and password.");
        const sessionResponse = await fetch("/api/v1/auth/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!sessionResponse.ok)
          throw new Error(
            "Sign-in succeeded, but your account could not be loaded. Refresh to continue.",
          );
        const session = (await sessionResponse.json()) as AccountSession;
        const redirect = new URLSearchParams(window.location.search).get(
          "redirect",
        );
        window.location.replace(
          safeDestination(redirect, destinationForRole(session.role)),
        );
      } else if (mode === "register") {
        const response = await fetch("/api/v1/auth/register", {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/json",
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ email, password }),
        });
        if (!response.ok)
          throw new Error(
            await responseError(
              response,
              "We could not send your verification email. Please try again shortly.",
            ),
          );
        const result = (await response.json()) as { message?: string };
        setMessage(
          result.message ??
            "We’ve received your request. If a new account can be created for this email, we’ll send a verification link. You can request another link below.",
        );
        setEmailInput(email);
        setMode("login");
      } else if (mode === "resend") {
        const response = await fetch("/api/v1/auth/resend-verification", {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/json",
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ email }),
        });
        if (!response.ok)
          throw new Error(
            await responseError(
              response,
              "We could not process your request. Please try again shortly.",
            ),
          );
        const result = (await response.json()) as { message?: string };
        setMessage(
          result.message ??
            "If an account with this email needs verification, we will send a new verification link shortly.",
        );
        setEmailInput(email);
        setMode("login");
      } else {
        const response = await fetch("/api/v1/auth/forgot-password", {
          method: "POST",
          credentials: "same-origin",
          headers: {
            "Content-Type": "application/json",
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ email }),
        });
        if (!response.ok)
          throw new Error("We could not start password recovery.");
        setMessage(
          "If the account exists, password recovery instructions have been sent.",
        );
      }
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Request failed. Try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <Link className="admin-brand" href="/" aria-label="MedNet home">
          <span className="admin-brand-mark" aria-hidden="true">
            M
          </span>
          <span>MedNet</span>
        </Link>
        <div className="admin-header-label">
          <span className="admin-header-kicker">Secure access</span>
          <span>Sign in</span>
        </div>
        <Link className="admin-public-link" href="/">
          Public site
        </Link>
      </header>
      <section className="admin-login-layout">
        <div className="admin-login-intro">
          <p className="admin-eyebrow">MedNet account</p>
          <h1>Sign in securely.</h1>
          <p>
            Use your email and password or continue with Google. New accounts
            start with patient access; provider access requires approval.
          </p>
        </div>
        <div className="admin-login-form">
          {googleError && (
            <p className="admin-error" role="alert">
              Google sign-in could not be completed. Try again or use your email
              and password.
            </p>
          )}
          <div
            className="admin-auth-modes"
            role="group"
            aria-label="Sign-in options"
          >
            <button
              type="button"
              className={
                mode === "login"
                  ? "admin-auth-mode admin-auth-mode-active"
                  : "admin-auth-mode"
              }
              aria-pressed={mode === "login"}
              onClick={() => setMode("login")}
            >
              Sign in
            </button>
            <button
              type="button"
              className={
                mode === "register"
                  ? "admin-auth-mode admin-auth-mode-active"
                  : "admin-auth-mode"
              }
              aria-pressed={mode === "register"}
              onClick={() => setMode("register")}
            >
              Create account
            </button>
          </div>
          <div className="admin-auth-actions">
            <button
              className="admin-auth-recovery"
              type="button"
              onClick={() => {
                setError("");
                setMessage("");
                setMode(mode === "forgot" ? "login" : "forgot");
              }}
            >
              {mode === "forgot" ? "Back to sign in" : "Forgot password?"}
            </button>
            {(mode === "resend" ||
              (mode === "login" && Boolean(error || message))) && (
              <button
                className="admin-auth-recovery"
                type="button"
                onClick={() => {
                  setError("");
                  setMessage("");
                  setMode(mode === "resend" ? "login" : "resend");
                }}
              >
                {mode === "resend"
                  ? "Back to sign in"
                  : "Resend verification email"}
              </button>
            )}
          </div>
          <form className="admin-account-form" onSubmit={submit}>
            <label htmlFor="account-email">Email address</label>
            <input
              id="account-email"
              name="email"
              type="email"
              autoComplete="email"
              value={emailInput}
              onChange={(event) => setEmailInput(event.target.value)}
              required
            />
            {(mode === "login" || mode === "register") && (
              <PasswordInput
                id="account-password"
                name="password"
                label="Password"
                minLength={12}
                autoComplete={
                  mode === "login" ? "current-password" : "new-password"
                }
              />
            )}
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            {message && (
              <p className="admin-form-note" role="status">
                {message}
              </p>
            )}
            {mode === "resend" && (
              <p className="admin-verification-note">
                Verification is completed using an email link, not a code. If
                this address has an account awaiting verification, we’ll send a
                fresh link.
              </p>
            )}
            <button className="admin-submit" type="submit" disabled={busy}>
              {busy
                ? "Working..."
                : mode === "login"
                  ? "Sign in"
                  : mode === "register"
                    ? "Create account"
                    : mode === "forgot"
                      ? "Send recovery email"
                      : "Resend verification email"}
            </button>
          </form>
          <span className="admin-data-label">Other sign-in options</span>
          {enabled === null ? (
            <p className="admin-form-note" aria-live="polite">
              Checking sign-in availability...
            </p>
          ) : enabled ? (
            <Link
              className="admin-submit admin-google-button"
              href="/api/v1/auth/oauth2/authorization/google"
              prefetch={false}
            >
              Continue with Google
            </Link>
          ) : (
            <p className="admin-error" role="status">
              Google sign-in is not configured for this deployment yet.
            </p>
          )}
          <p className="admin-form-note">
            Google verifies your email. MedNet keeps access roles and protected
            data on its backend.
          </p>
        </div>
      </section>
    </main>
  );
}

export function UserAccountPortal() {
  const [session, setSession] = useState<AccountSession | null>(null);
  const [state, setState] = useState<"loading" | "signed-out" | "signed-in">(
    "loading",
  );
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    async function loadSession() {
      try {
        const response = await fetch("/api/v1/auth/session", {
          cache: "no-store",
        });
        if (!active) return;
        if (!response.ok) {
          setState("signed-out");
          return;
        }
        const currentSession = (await response.json()) as AccountSession;
        if (
          currentSession.role === "ADMIN" ||
          currentSession.role === "SUPER_ADMIN"
        ) {
          window.location.replace("/admin");
          return;
        }
        setSession(currentSession);
        setState("signed-in");
      } catch {
        if (!active) return;
        setError("The MedNet API is unavailable. Try again shortly.");
        setState("signed-out");
      }
    }
    void loadSession();
    return () => {
      active = false;
    };
  }, []);

  async function signOut() {
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
      });
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        headers: { [csrf.headerName]: csrf.token },
      });
      setSession(null);
      setState("signed-out");
    } catch {
      setError("Sign-out failed. Close this browser session and try again.");
    }
  }

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <Link className="admin-brand" href="/" aria-label="MedNet home">
          <span className="admin-brand-mark" aria-hidden="true">
            M
          </span>
          <span>MedNet</span>
        </Link>
        <div className="admin-header-label">
          <span className="admin-header-kicker">Personal access</span>
          <span>Account</span>
        </div>
      </header>
      <section className="admin-content">
        {state === "loading" ? (
          <div className="admin-state" aria-live="polite">
            Checking your session...
          </div>
        ) : state === "signed-out" ? (
          <div className="admin-not-configured">
            <h1>Sign in to continue</h1>
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <Link className="admin-submit admin-google-button" href="/sign-in">
              Go to sign in
            </Link>
          </div>
        ) : (
          <>
            <p className="admin-eyebrow">Signed in</p>
            <h1>{session?.email}</h1>
            <p className="admin-account-role">Account type: {session?.role}</p>
            <p className="admin-module-note">
              Patient and provider service workflows are being connected
              separately. No clinical information is displayed here.
            </p>
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <button className="admin-signout" type="button" onClick={signOut}>
              Sign out
            </button>
          </>
        )}
      </section>
    </main>
  );
}
