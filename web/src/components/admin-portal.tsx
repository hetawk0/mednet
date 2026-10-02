"use client";

import { type FormEvent, useEffect, useState } from "react";
import Link from "next/link";

type AccessState = "checking" | "signed-out" | "signed-in";

type AdminSession = {
  email: string;
  role: string;
};

type AdminOverview = {
  apiStatus: string;
  checkedAt: string;
  uptimeSeconds: number;
  modules: Array<{ key: string; status: string }>;
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

const moduleLabels: Record<string, string> = {
  providerReview: "Provider review",
  accountSupport: "Account support",
  serviceRequests: "Service requests",
  auditTrail: "Administrative audit trail",
};

export function AdminPortal() {
  const [access, setAccess] = useState<AccessState>("checking");
  const [session, setSession] = useState<AdminSession | null>(null);
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function refreshOverview() {
    const response = await fetch("/api/v1/admin/overview", {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!response.ok) throw new Error("The admin API could not be reached.");
    setOverview((await response.json()) as AdminOverview);
  }

  async function checkSession() {
    try {
      const response = await fetch("/api/v1/auth/admin/session", {
        cache: "no-store",
        credentials: "same-origin",
      });
      if (!response.ok) {
        setAccess("signed-out");
        return;
      }

      setSession((await response.json()) as AdminSession);
      await refreshOverview();
      setAccess("signed-in");
    } catch {
      setError("The MedNet API is unavailable. Try again shortly.");
      setAccess("signed-out");
    }
  }

  useEffect(() => {
    let active = true;

    async function loadSession() {
      try {
        const response = await fetch("/api/v1/auth/admin/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (!response.ok) {
          setAccess("signed-out");
          return;
        }

        const currentSession = (await response.json()) as AdminSession;
        const overviewResponse = await fetch("/api/v1/admin/overview", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!overviewResponse.ok) {
          throw new Error("The admin API could not be reached.");
        }

        const currentOverview = (await overviewResponse.json()) as AdminOverview;
        if (!active) return;
        setSession(currentSession);
        setOverview(currentOverview);
        setAccess("signed-in");
      } catch {
        if (!active) return;
        setError("The MedNet API is unavailable. Try again shortly.");
        setAccess("signed-out");
      }
    }

    void loadSession();
    return () => {
      active = false;
    };
  }, []);

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email") ?? "").trim();
    const password = String(formData.get("password") ?? "");

    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
        credentials: "same-origin",
      });
      if (!csrfResponse.ok) {
        throw new Error("Admin sign-in is not available. Check the API deployment.");
      }

      const csrf = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch("/api/v1/auth/admin/login", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          [csrf.headerName]: csrf.token,
        },
        body: new URLSearchParams({ email, password }),
      });

      if (!response.ok) {
        throw new Error("Sign-in failed. Check your credentials and LPAD admin setup.");
      }

      await checkSession();
    } catch (loginError) {
      setError(
        loginError instanceof Error
          ? loginError.message
          : "Sign-in failed. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleLogout() {
    setBusy(true);
    setError("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
        credentials: "same-origin",
      });
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      await fetch("/api/v1/auth/admin/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
      });
      setSession(null);
      setOverview(null);
      setAccess("signed-out");
    } catch {
      setError("Sign-out failed. Close this browser session and try again.");
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
          <span className="admin-header-kicker">Platform operations</span>
          <span>Administration</span>
        </div>
        {access === "signed-in" && session ? (
          <div className="admin-identity">
            <span>{session.email}</span>
            <button
              className="admin-signout"
              type="button"
              onClick={handleLogout}
              disabled={busy}
            >
              Sign out
            </button>
          </div>
        ) : (
          <Link className="admin-public-link" href="/">
            Public site
          </Link>
        )}
      </header>

      {access === "checking" ? (
        <section className="admin-state" aria-live="polite">
          Checking administrator session...
        </section>
      ) : access === "signed-out" ? (
        <section className="admin-login-layout">
          <div className="admin-login-intro">
            <p className="admin-eyebrow">Restricted access</p>
            <h1>Administrator sign in</h1>
            <p>
              Sign in with the administrator account configured for this MedNet
              deployment.
            </p>
          </div>
          <form className="admin-login-form" onSubmit={handleLogin}>
            <label htmlFor="admin-email">Administrator email</label>
            <input
              id="admin-email"
              name="email"
              type="email"
              autoComplete="username"
              required
            />
            <label htmlFor="admin-password">Password</label>
            <input
              id="admin-password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <button className="admin-submit" type="submit" disabled={busy}>
              {busy ? "Signing in..." : "Sign in"}
            </button>
            <p className="admin-form-note">
              Accounts are provisioned by the MedNet operator. There is no
              public administrator registration.
            </p>
          </form>
        </section>
      ) : (
        <section className="admin-content">
          <div className="admin-page-heading">
            <div>
              <p className="admin-eyebrow">Operations</p>
              <h1>System overview</h1>
              <p>Backend status and administrator module readiness.</p>
            </div>
            <button
              className="admin-refresh"
              type="button"
              onClick={() => void refreshOverview().catch(() =>
                setError("The admin API could not be reached."),
              )}
            >
              Refresh status
            </button>
          </div>

          {error && (
            <p className="admin-error" role="alert">
              {error}
            </p>
          )}

          {overview ? (
            <>
              <div className="admin-status-strip">
                <div>
                  <span className="admin-data-label">Spring Boot API</span>
                  <strong className="admin-health-value">
                    <span className="admin-health-dot" aria-hidden="true" />
                    {overview.apiStatus}
                  </strong>
                </div>
                <div>
                  <span className="admin-data-label">API uptime</span>
                  <strong>{Math.floor(overview.uptimeSeconds / 3600)}h</strong>
                </div>
                <div>
                  <span className="admin-data-label">Last checked</span>
                  <strong>{new Date(overview.checkedAt).toLocaleString()}</strong>
                </div>
              </div>

              <section className="admin-module-section">
                <div className="admin-section-heading">
                  <div>
                    <p className="admin-eyebrow">Administration modules</p>
                    <h2>Workflow connectivity</h2>
                  </div>
                  <span className="admin-readonly-label">Status only</span>
                </div>
                <div className="admin-module-list">
                  {overview.modules.map((module) => (
                    <div className="admin-module-row" key={module.key}>
                      <span>{moduleLabels[module.key] ?? module.key}</span>
                      <span className="admin-module-status">
                        {module.status === "NOT_IMPLEMENTED"
                          ? "Not connected"
                          : module.status}
                      </span>
                    </div>
                  ))}
                </div>
                <p className="admin-module-note">
                  These workflows are intentionally not represented as
                  operational controls until their data models and authorization
                  rules are implemented.
                </p>
              </section>
            </>
          ) : (
            <div className="admin-state" aria-live="polite">
              Loading backend status...
            </div>
          )}
        </section>
      )}
    </main>
  );
}