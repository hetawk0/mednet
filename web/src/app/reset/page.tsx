"use client";

import { type FormEvent, useState } from "react";
import Link from "next/link";
import { PasswordInput } from "@/components/password-input";

export default function ResetPage() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    const params = new URLSearchParams(window.location.search);
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/v1/auth/reset-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: params.get("email"),
          token: params.get("token"),
          password: form.get("password"),
        }),
      });
      const body = (await response.json()) as {
        message?: string;
        detail?: string;
      };
      if (!response.ok)
        throw new Error(
          body.detail ?? "This reset link is invalid or expired.",
        );
      setMessage(body.message ?? "Password updated. You can now sign in.");
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

  return (
    <main className="admin-shell">
      <section className="admin-login-layout">
        <div className="admin-login-intro">
          <p className="admin-eyebrow">Account security</p>
          <h1>Choose a new password.</h1>
          <p>Use at least 12 characters and keep it unique to MedNet.</p>
        </div>
        <form className="admin-login-form" onSubmit={submit}>
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
          {message && (
            <p className="admin-form-note" role="status">
              {message}
            </p>
          )}
          <button className="admin-submit" type="submit" disabled={busy}>
            {busy ? "Updating..." : "Update password"}
          </button>
          <Link className="admin-google-link" href="/sign-in">
            Back to sign in
          </Link>
        </form>
      </section>
    </main>
  );
}
