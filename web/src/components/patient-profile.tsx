"use client";

import { type FormEvent, useEffect, useState } from "react";
import Link from "next/link";

type AccountSession = { email: string; role: string };
type CsrfResponse = { headerName: string; token: string };
type PatientProfileValues = {
  fullName: string;
  dateOfBirth: string;
  phoneNumber: string;
  address: string;
};

const localDate = new Date(
  Date.now() - new Date().getTimezoneOffset() * 60_000,
)
  .toISOString()
  .slice(0, 10);

const emptyProfile: PatientProfileValues = {
  fullName: "",
  dateOfBirth: "",
  phoneNumber: "",
  address: "",
};

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

export function PatientProfile() {
  const [access, setAccess] = useState<
    "checking" | "signed-out" | "ready" | "error"
  >("checking");
  const [profile, setProfile] = useState<PatientProfileValues>(emptyProfile);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function loadProfile() {
      try {
        const sessionResponse = await fetch("/api/v1/auth/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (!sessionResponse.ok) {
          setAccess("signed-out");
          return;
        }

        const session = (await sessionResponse.json()) as AccountSession;
        if (session.role !== "PATIENT") {
          const destination =
            session.role === "ADMIN" || session.role === "SUPER_ADMIN"
              ? "/admin"
              : "/account";
          window.location.replace(destination);
          return;
        }

        const profileResponse = await fetch("/api/v1/patients/me/profile", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (profileResponse.status === 404) {
          setAccess("ready");
          return;
        }
        if (!profileResponse.ok) {
          throw new Error(
            await responseError(
              profileResponse,
              "Your profile could not be loaded.",
            ),
          );
        }
        const savedProfile =
          (await profileResponse.json()) as PatientProfileValues;
        setProfile({
          fullName: savedProfile.fullName,
          dateOfBirth: savedProfile.dateOfBirth,
          phoneNumber: savedProfile.phoneNumber,
          address: savedProfile.address,
        });
        setAccess("ready");
      } catch (loadError) {
        if (!active) return;
        setError(
          loadError instanceof Error
            ? loadError.message
            : "Your profile could not be loaded.",
        );
        setAccess("error");
      }
    }

    void loadProfile();
    return () => {
      active = false;
    };
  }, []);

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
        credentials: "same-origin",
      });
      if (!csrfResponse.ok) throw new Error("Could not prepare your update.");
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch("/api/v1/patients/me/profile", {
        method: "PUT",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(profile),
      });
      if (!response.ok) {
        throw new Error(
          await responseError(response, "Your profile could not be saved."),
        );
      }
      const savedProfile =
        (await response.json()) as PatientProfileValues;
      setProfile({
        fullName: savedProfile.fullName,
        dateOfBirth: savedProfile.dateOfBirth,
        phoneNumber: savedProfile.phoneNumber,
        address: savedProfile.address,
      });
      setMessage("Your profile was saved.");
    } catch (saveError) {
      setError(
        saveError instanceof Error
          ? saveError.message
          : "Your profile could not be saved.",
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
          <span className="admin-header-kicker">Patient portal</span>
          <span>Profile details</span>
        </div>
        <Link className="admin-public-link" href="/patient">
          Dashboard
        </Link>
      </header>
      {access === "checking" ? (
        <p className="admin-state" role="status">
          Checking your session...
        </p>
      ) : access === "signed-out" ? (
        <section className="admin-content">
          <div className="admin-not-configured">
            <h1>Sign in to continue</h1>
            <Link className="admin-submit admin-google-button" href="/sign-in">
              Go to sign in
            </Link>
          </div>
        </section>
      ) : access === "error" ? (
        <section className="admin-content">
          <h1>Your profile is unavailable.</h1>
          <p className="admin-error" role="alert">
            {error}
          </p>
          <Link className="text-link" href="/patient">
            Return to dashboard <span aria-hidden="true">-&gt;</span>
          </Link>
        </section>
      ) : (
        <section className="admin-content patient-profile-page">
          <p className="admin-eyebrow">Patient profile</p>
          <h1>Your details</h1>
          <p className="admin-module-note">
            These details are visible to you and are used to support your
            patient account. Do not include clinical notes here.
          </p>
          <form className="patient-profile-form" onSubmit={saveProfile}>
            <label>
              Full name
              <input
                autoComplete="name"
                maxLength={160}
                required
                value={profile.fullName}
                onChange={(event) =>
                  setProfile({ ...profile, fullName: event.target.value })
                }
              />
            </label>
            <label>
              Date of birth
              <input
                autoComplete="bday"
                max={localDate}
                required
                type="date"
                value={profile.dateOfBirth}
                onChange={(event) =>
                  setProfile({ ...profile, dateOfBirth: event.target.value })
                }
              />
            </label>
            <label>
              Phone number
              <input
                autoComplete="tel"
                maxLength={32}
                required
                type="tel"
                value={profile.phoneNumber}
                onChange={(event) =>
                  setProfile({ ...profile, phoneNumber: event.target.value })
                }
              />
            </label>
            <label>
              Address
              <textarea
                autoComplete="street-address"
                maxLength={500}
                required
                rows={3}
                value={profile.address}
                onChange={(event) =>
                  setProfile({ ...profile, address: event.target.value })
                }
              />
            </label>
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            {message && (
              <p className="patient-profile-success" role="status">
                {message}
              </p>
            )}
            <button className="admin-submit" type="submit" disabled={busy}>
              {busy ? "Saving..." : "Save profile"}
            </button>
          </form>
        </section>
      )}
    </main>
  );
}
