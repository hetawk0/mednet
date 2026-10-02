"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

type GoogleStatus = { enabled: boolean };
type AccountSession = { email: string; role: string };
type CsrfResponse = { headerName: string; token: string };

export function GoogleSignIn() {
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    let active = true;
    async function checkGoogleStatus() {
      try {
        const response = await fetch("/api/v1/auth/google/status", { cache: "no-store" });
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

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <Link className="admin-brand" href="/" aria-label="MedNet home">
          <span className="admin-brand-mark" aria-hidden="true">M</span>
          <span>MedNet</span>
        </Link>
        <div className="admin-header-label">
          <span className="admin-header-kicker">Secure access</span>
          <span>Sign in</span>
        </div>
        <Link className="admin-public-link" href="/">Public site</Link>
      </header>
      <section className="admin-login-layout">
        <div className="admin-login-intro">
          <p className="admin-eyebrow">MedNet account</p>
          <h1>Sign in securely.</h1>
          <p>Use your Google account to continue. New accounts start with patient access; provider access requires approval.</p>
        </div>
        <div className="admin-login-form">
          <span className="admin-data-label">Google account</span>
          {enabled === null ? (
            <p className="admin-form-note" aria-live="polite">Checking sign-in availability...</p>
          ) : enabled ? (
            <Link className="admin-submit admin-google-button" href="/api/v1/auth/oauth2/authorization/google" prefetch={false}>
              Continue with Google
            </Link>
          ) : (
            <p className="admin-error" role="status">
              Google sign-in is not configured for this deployment yet.
            </p>
          )}
          <p className="admin-form-note">Google verifies your email. MedNet keeps access roles and protected data on its backend.</p>
          <p className="admin-form-note">Administrators can also sign in at <Link href="/admin">the admin area</Link>.</p>
        </div>
      </section>
    </main>
  );
}

export function UserAccountPortal() {
  const [session, setSession] = useState<AccountSession | null>(null);
  const [state, setState] = useState<"loading" | "signed-out" | "signed-in">("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    async function loadSession() {
      try {
        const response = await fetch("/api/v1/auth/session", { cache: "no-store" });
        if (!active) return;
        if (!response.ok) {
          setState("signed-out");
          return;
        }
        setSession((await response.json()) as AccountSession);
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
      const csrfResponse = await fetch("/api/v1/auth/csrf", { cache: "no-store" });
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
          <span className="admin-brand-mark" aria-hidden="true">M</span>
          <span>MedNet</span>
        </Link>
        <div className="admin-header-label">
          <span className="admin-header-kicker">Personal access</span>
          <span>Account</span>
        </div>
      </header>
      <section className="admin-content">
        {state === "loading" ? (
          <div className="admin-state" aria-live="polite">Checking your session...</div>
        ) : state === "signed-out" ? (
          <div className="admin-not-configured">
            <h1>Sign in to continue</h1>
            {error && <p className="admin-error" role="alert">{error}</p>}
            <Link className="admin-submit admin-google-button" href="/sign-in">Go to sign in</Link>
          </div>
        ) : (
          <>
            <p className="admin-eyebrow">Signed in</p>
            <h1>{session?.email}</h1>
            <p className="admin-account-role">Account type: {session?.role}</p>
            <p className="admin-module-note">Patient and provider service workflows are being connected separately. No clinical information is displayed here.</p>
            {error && <p className="admin-error" role="alert">{error}</p>}
            <button className="admin-signout" type="button" onClick={signOut}>Sign out</button>
          </>
        )}
      </section>
    </main>
  );
}