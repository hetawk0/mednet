"use client";

import { type FormEvent, use, useState } from "react";
import Link from "next/link";
import { PasswordInput } from "@/components/password-input";

type ResetStep = "request" | "verify" | "reset" | "done";
type CsrfResponse = { headerName: string; token: string };
type ResetResponse = {
  token?: string | null;
  message?: string;
  detail?: string;
  error?: string;
};

async function csrfHeaders() {
  const response = await fetch("/api/v1/auth/csrf", {
    credentials: "same-origin",
  });
  if (!response.ok) throw new Error("Could not start a secure request.");
  const csrf = (await response.json()) as CsrfResponse;
  return {
    "Content-Type": "application/json",
    [csrf.headerName]: csrf.token,
  };
}

async function responseBody(response: Response, fallback: string) {
  const body = (await response.json()) as ResetResponse;
  if (!response.ok) {
    throw new Error(body.detail ?? body.error ?? body.message ?? fallback);
  }
  return body;
}

export default function ResetPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string | string[] }>;
}) {
  const parameters = use(searchParams);
  const requestedEmail =
    typeof parameters.email === "string" ? parameters.email : "";
  const [step, setStep] = useState<ResetStep>(
    requestedEmail ? "verify" : "request",
  );
  const [email, setEmail] = useState(requestedEmail);
  const [code, setCode] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function sendCode(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch("/api/v1/auth/forgot-password", {
        method: "POST",
        credentials: "same-origin",
        headers: await csrfHeaders(),
        body: JSON.stringify({ email }),
      });
      const body = await responseBody(response, "Could not send a reset code.");
      setMessage(
        body.message ??
          "If the account exists, a password reset code has been sent.",
      );
      setStep("verify");
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Could not send a reset code.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await fetch("/api/v1/auth/reset-password/verify", {
        method: "POST",
        credentials: "same-origin",
        headers: await csrfHeaders(),
        body: JSON.stringify({ email, code }),
      });
      const body = await responseBody(
        response,
        "The code is invalid or expired. Request a new code.",
      );
      if (!body.token) throw new Error("Could not verify that code.");
      setResetToken(body.token);
      setStep("reset");
      setMessage("");
      window.history.replaceState(null, "", "/reset");
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Could not verify that code.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function updatePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/v1/auth/reset-password", {
        method: "POST",
        credentials: "same-origin",
        headers: await csrfHeaders(),
        body: JSON.stringify({
          email,
          token: resetToken,
          password: form.get("password"),
        }),
      });
      const body = await responseBody(response, "Password reset failed.");
      setMessage(body.message ?? "Password updated. You can now sign in.");
      setResetToken("");
      setCode("");
      setStep("done");
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Password reset failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  const heading = {
    request: "Reset your password.",
    verify: "Verify your email.",
    reset: "Choose a new password.",
    done: "Password updated.",
  }[step];
  const description = {
    request: "Enter your email and we will send a one-time recovery code.",
    verify: "Enter the six-digit code sent to your email address.",
    reset: "Your email is verified. Choose a new password for your account.",
    done: "Your password has been changed successfully.",
  }[step];

  return (
    <main className="admin-shell">
      <section className="admin-login-layout">
        <div className="admin-login-intro">
          <p className="admin-eyebrow">Account security</p>
          <h1>{heading}</h1>
          <p>{description}</p>
        </div>
        {step === "request" ? (
          <form className="admin-login-form" onSubmit={sendCode}>
            <label htmlFor="reset-email">Email address</label>
            <input
              id="reset-email"
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
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
            <button className="admin-submit" type="submit" disabled={busy}>
              {busy ? "Sending..." : "Send recovery code"}
            </button>
            <Link className="admin-google-link" href="/sign-in">
              Back to sign in
            </Link>
          </form>
        ) : step === "verify" ? (
          <div className="admin-login-form">
            <p className="admin-form-note">
              Recovery code for <strong>{email}</strong>
            </p>
            <form className="admin-account-form" onSubmit={verifyCode}>
              <label htmlFor="reset-code">Six-digit code</label>
              <input
                id="reset-code"
                name="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(event) =>
                  setCode(event.target.value.replace(/\D/g, ""))
                }
                required
              />
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
              <button className="admin-submit" type="submit" disabled={busy}>
                {busy ? "Verifying..." : "Verify code"}
              </button>
            </form>
            <button
              className="admin-auth-recovery"
              type="button"
              onClick={() => void sendCode()}
              disabled={busy}
            >
              Send a new code
            </button>
            <button
              className="admin-auth-recovery"
              type="button"
              onClick={() => {
                setStep("request");
                setCode("");
                setError("");
                setMessage("");
              }}
            >
              Use a different email
            </button>
            <Link className="admin-google-link" href="/sign-in">
              Back to sign in
            </Link>
          </div>
        ) : step === "reset" ? (
          <form className="admin-login-form" onSubmit={updatePassword}>
            <PasswordInput
              id="reset-password"
              name="password"
              label="New password"
              minLength={12}
              autoComplete="new-password"
            />
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <button className="admin-submit" type="submit" disabled={busy}>
              {busy ? "Updating..." : "Update password"}
            </button>
          </form>
        ) : (
          <div className="admin-login-form">
            <p className="admin-form-note" role="status">
              {message}
            </p>
            <Link className="admin-submit admin-google-button" href="/sign-in">
              Continue to sign in
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}
