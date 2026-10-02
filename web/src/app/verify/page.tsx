"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

export default function VerifyPage() {
  const [message, setMessage] = useState("Verifying your email...");
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const email = params.get("email");
    const token = params.get("token");
    const verification =
      email && token
        ? fetch(
            `/api/v1/auth/verify?email=${encodeURIComponent(email)}&token=${encodeURIComponent(token)}`,
          )
        : Promise.reject(new Error("This verification link is incomplete."));
    verification
      .then(async (response) => {
        const body = (await response.json()) as {
          message?: string;
          detail?: string;
        };
        if (!response.ok)
          throw new Error(
            body.detail ?? "This verification link is invalid or expired.",
          );
        setMessage(body.message ?? "Email verified. You can now sign in.");
      })
      .catch((error: unknown) =>
        setMessage(
          error instanceof Error ? error.message : "Verification failed.",
        ),
      );
  }, []);

  return (
    <main className="admin-shell">
      <section className="admin-content">
        <p className="admin-eyebrow">Account verification</p>
        <h1>Confirm your email.</h1>
        <p className="admin-module-note">{message}</p>
        <Link className="admin-submit admin-google-button" href="/sign-in">
          Continue to sign in
        </Link>
      </section>
    </main>
  );
}
