"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { TextInput } from "@/components/ui/inputs";
import { PasswordField } from "@/components/ui/password-field";
import { StateMessage } from "@/components/ui/state-message";
import { homePathForSession } from "@/features/workspace/workspaces";
import { ApiRequestError, getSession } from "@/lib/api";
import { emailProblem, passwordProblem, register } from "./auth-api";

type FieldErrors = { email?: string; password?: string };

/** Where an existing account continues its provider application: sign-in returns to the doctor workspace. */
export const CONTINUE_APPLICATION_HREF = `/sign-in?redirect=${encodeURIComponent("/provider")}`;

/**
 * Step 1 of provider sign-up: creates a PROVIDER account through the same registration endpoint and
 * email verification as patients. Professional details are submitted after the first sign-in, because
 * `POST /providers/applications` requires a signed-in, verified account.
 */
export function ProviderSignUpPanel() {
  const router = useRouter();
  const [sessionChecked, setSessionChecked] = useState(false);
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState("");
  const [duplicate, setDuplicate] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    getSession()
      .then((session) => {
        if (!active) return;
        // Already signed in: continue from the right workspace instead of creating a second account.
        if (session) return window.location.replace(homePathForSession(session));
        setSessionChecked(true);
      })
      .catch(() => active && setSessionChecked(true));
    return () => {
      active = false;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const password = String(new FormData(event.currentTarget).get("password") ?? "");
    const trimmed = email.trim();
    const errors: FieldErrors = {
      email: emailProblem(trimmed) ?? undefined,
      password: passwordProblem(password) ?? undefined,
    };
    setFieldErrors(errors);
    setError("");
    setDuplicate(false);
    if (errors.email) return emailRef.current?.focus();
    if (errors.password) return passwordRef.current?.focus();

    setBusy(true);
    try {
      await register(trimmed, password, "PROVIDER");
      router.push(`/verify?email=${encodeURIComponent(trimmed)}`);
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 409) {
        setError("An account with this email already exists.");
        setDuplicate(true);
      } else {
        setError(cause instanceof Error ? cause.message : "Your account could not be created. Try again.");
      }
      setBusy(false);
    }
  }

  if (!sessionChecked) {
    return <StateMessage kind="loading" layout="inline" title="Checking your session…" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h2 className="text-title text-ink">Create your provider account</h2>
        <p className="mt-1 text-data text-text-secondary">
          Use the email address you want patients and MedNet administrators to reach you at. After you confirm it,
          sign in to submit your licence or registration details for review.
        </p>
      </div>

      <form noValidate onSubmit={submit} className="flex flex-col gap-5" aria-label="Create your provider account">
        <TextInput
          ref={emailRef}
          name="email"
          type="email"
          label="Professional email address"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors.email}
        />
        <PasswordField
          ref={passwordRef}
          name="password"
          label="Password"
          required
          autoComplete="new-password"
          description="At least 12 characters."
          error={fieldErrors.password}
        />

        {error ? (
          <Alert tone="critical" live="assertive" title={error}>
            {duplicate
              ? "If you have already started a provider application, sign in to continue it. Reset your password if you can’t access the account."
              : undefined}
          </Alert>
        ) : null}
        {duplicate ? (
          <div className="-mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-data font-semibold">
            <Link href={CONTINUE_APPLICATION_HREF} className="min-h-10 content-center rounded-sm text-green-800 hover:underline">
              Sign in to continue
            </Link>
            <Link
              href={`/reset?email=${encodeURIComponent(email.trim())}`}
              className="min-h-10 content-center rounded-sm text-green-800 hover:underline"
            >
              Reset password
            </Link>
          </div>
        ) : null}

        <Button type="submit" fullWidth loading={busy}>
          Create provider account
        </Button>
      </form>

      <div className="flex flex-col gap-1 border-t border-border pt-5 text-data">
        <Link href={CONTINUE_APPLICATION_HREF} className="min-h-10 content-center rounded-sm font-semibold text-green-800 hover:underline">
          Already applied? Sign in to continue
        </Link>
        <Link href="/sign-in?mode=register" className="min-h-10 content-center rounded-sm text-text-secondary hover:text-action">
          Looking for care? Create a patient account
        </Link>
      </div>
    </div>
  );
}
