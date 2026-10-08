"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

type AccountSession = { email: string; role: string };
type CsrfResponse = { headerName: string; token: string };
type CountState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; count: number };

async function countItems(url: string): Promise<number> {
  const response = await fetch(url, {
    cache: "no-store",
    credentials: "same-origin",
  });
  if (!response.ok) throw new Error("Patient activity could not be loaded.");

  const result: unknown = await response.json();
  if (Array.isArray(result)) return result.length;
  if (
    typeof result === "object" &&
    result !== null &&
    "totalElements" in result &&
    typeof result.totalElements === "number"
  ) {
    return result.totalElements;
  }
  throw new Error("The patient activity response was invalid.");
}

export function PatientDashboard() {
  const [session, setSession] = useState<AccountSession | null>(null);
  const [access, setAccess] = useState<"checking" | "signed-out" | "signed-in">(
    "checking",
  );
  const [appointments, setAppointments] = useState<CountState>({
    status: "loading",
  });
  const [homeCareRequests, setHomeCareRequests] = useState<CountState>({
    status: "loading",
  });
  const [labRequests, setLabRequests] = useState<CountState>({
    status: "loading",
  });
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadActivity(
      url: string,
      setState: (value: CountState) => void,
    ) {
      try {
        const count = await countItems(url);
        if (active) setState({ status: "ready", count });
      } catch {
        if (active) setState({ status: "error" });
      }
    }

    async function loadDashboard() {
      try {
        const response = await fetch("/api/v1/auth/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (!response.ok) {
          setAccess("signed-out");
          return;
        }

        const currentSession = (await response.json()) as AccountSession;
        if (currentSession.role !== "PATIENT") {
          const destination =
            currentSession.role === "ADMIN" ||
            currentSession.role === "SUPER_ADMIN"
              ? "/admin"
              : "/account";
          window.location.replace(destination);
          return;
        }

        setSession(currentSession);
        setAccess("signed-in");
        void loadActivity("/api/v1/appointments?page=0&size=1", setAppointments);
        void loadActivity("/api/v1/home-care-requests", setHomeCareRequests);
        void loadActivity("/api/v1/lab-requests", setLabRequests);
      } catch {
        if (!active) return;
        setError("The MedNet API is unavailable. Try again shortly.");
        setAccess("signed-out");
      }
    }

    void loadDashboard();
    return () => {
      active = false;
    };
  }, []);

  async function signOut() {
    setError("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
        credentials: "same-origin",
      });
      if (!csrfResponse.ok) throw new Error("Could not prepare sign-out.");
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
      });
      if (!response.ok) throw new Error("Sign-out request failed.");
      setSession(null);
      setAccess("signed-out");
    } catch {
      setError("Sign-out failed. Close this browser session and try again.");
    }
  }

  function countLabel(value: CountState, noun: string) {
    if (value.status === "loading") return "Loading...";
    if (value.status === "error") return "Unable to load";
    return `${value.count} ${noun}${value.count === 1 ? "" : "s"}`;
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
          <span className="admin-header-kicker">Patient portal</span>
          <span>My care</span>
        </div>
        {access === "signed-in" && (
          <button
            className="admin-signout patient-signout"
            type="button"
            onClick={signOut}
          >
            Sign out
          </button>
        )}
      </header>
      {access === "checking" ? (
        <p className="admin-state" role="status">
          Checking your session...
        </p>
      ) : access === "signed-out" ? (
        <section className="admin-content">
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
        </section>
      ) : (
        <section className="admin-content patient-dashboard">
          <div className="admin-page-heading">
            <div>
              <p className="admin-eyebrow">Patient dashboard</p>
              <h1>Your care, organized.</h1>
              <p className="admin-module-note">
                Signed in as {session?.email}. Your dashboard shows activity
                counts only; clinical details stay in their protected
                workflows.
              </p>
            </div>
          </div>
          {error && (
            <p className="admin-error" role="alert">
              {error}
            </p>
          )}
          <div className="patient-dashboard-grid">
            <article className="patient-dashboard-card">
              <p className="admin-eyebrow">Account</p>
              <h2>Profile details</h2>
              <p>Keep your name and contact information up to date.</p>
              <Link className="text-link" href="/patient/profile">
                View your profile <span aria-hidden="true">-&gt;</span>
              </Link>
            </article>
            <article className="patient-dashboard-card">
              <p className="admin-eyebrow">Care coordination</p>
              <h2>Appointments</h2>
              <p className="patient-dashboard-count">
                {countLabel(appointments, "appointment")}
              </p>
              <Link className="text-link" href="/services#appointments">
                Explore appointment services{" "}
                <span aria-hidden="true">-&gt;</span>
              </Link>
            </article>
            <article className="patient-dashboard-card">
              <p className="admin-eyebrow">Patient services</p>
              <h2>Service requests</h2>
              <p className="patient-dashboard-count">
                {countLabel(homeCareRequests, "home-care request")} ·{" "}
                {countLabel(labRequests, "laboratory request")}
              </p>
              <Link className="text-link" href="/services#service-requests">
                Explore patient services{" "}
                <span aria-hidden="true">-&gt;</span>
              </Link>
            </article>
          </div>
          <p className="patient-dashboard-note">
            Appointment and service-request management screens are not yet
            available on the web. These counts come from your authenticated
            patient APIs.
          </p>
        </section>
      )}
    </main>
  );
}
