"use client";

import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink, buttonClasses } from "@/components/ui/button";
import { TextInput } from "@/components/ui/inputs";
import { PasswordField } from "@/components/ui/password-field";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { StateMessage } from "@/components/ui/state-message";
import { ApiRequestError, getSession } from "@/lib/api";
import {
  emailProblem,
  googleAuthEnabled,
  passwordProblem,
  register,
  requestPasswordReset,
  resendVerification,
  signIn,
} from "./auth-api";
import { destinationAfterSignIn } from "./destination";

type Mode = "login" | "register" | "forgot" | "resend";
type FieldErrors = { email?: string; password?: string };

const submitLabels: Record<Mode, string> = {
  login: "Sign in",
  register: "Create account",
  forgot: "Send recovery instructions",
  resend: "Resend verification email",
};

const headings: Record<Mode, string> = {
  login: "Sign in to MedNet",
  register: "Create a patient account",
  forgot: "Recover your password",
  resend: "Resend verification email",
};

function goTo(destination: string) {
  window.location.replace(destination);
}

/**
 * MedNet sign-in, registration, verification resend and password-recovery request.
 * New accounts start as patients; provider access needs approval.
 */
export function SignInPanel() {
  const router = useRouter();
  const [sessionChecked, setSessionChecked] = useState(false);
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [registrationDuplicate, setRegistrationDuplicate] = useState(false);
  const [offerResend, setOfferResend] = useState(false);
  const [recoverySent, setRecoverySent] = useState(false);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const [googleStatusError, setGoogleStatusError] = useState("");
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let active = true;
    Promise.allSettled([getSession(), googleAuthEnabled()])
      .then(([sessionResult, googleResult]) => {
        if (!active) return;
        const params = new URLSearchParams(window.location.search);
        if (params.get("googleError") === "1") {
          setError("Google sign-in could not be completed. Try again or sign in with your email and password.");
        }
        if (sessionResult.status === "fulfilled" && sessionResult.value) {
          goTo(destinationAfterSignIn(
            sessionResult.value.role,
            window.location.search,
            window.location.origin,
            sessionResult.value.accountType,
          ));
          return;
        }
        // `?mode=register` opens patient sign-up directly (linked from provider sign-up).
        if (params.get("mode") === "register") setMode("register");
        if (googleResult.status === "fulfilled") {
          setGoogleEnabled(googleResult.value);
        } else {
          setGoogleStatusError("Google sign-in availability could not be checked.");
        }
        setSessionChecked(true);
      })
      .catch(() => {
        if (active) setSessionChecked(true);
      });
    return () => {
      active = false;
    };
  }, []);

  function switchMode(next: Mode) {
    setMode(next);
    setError("");
    setMessage("");
    setRegistrationDuplicate(false);
    setFieldErrors({});
    setOfferResend(false);
    setRecoverySent(false);
  }

  const needsPassword = mode === "login" || mode === "register";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const password = String(new FormData(event.currentTarget).get("password") ?? "");
    const trimmed = email.trim();
    const errors: FieldErrors = { email: emailProblem(trimmed) ?? undefined };
    if (mode === "login" && !password) errors.password = "Enter your password.";
    if (mode === "register") errors.password = passwordProblem(password) ?? undefined;
    setFieldErrors(errors);
    if (errors.email) return emailRef.current?.focus();
    if (needsPassword && errors.password) return passwordRef.current?.focus();

    setBusy(true);
    setError("");
    setMessage("");
    setOfferResend(false);
    setRecoverySent(false);
    setRegistrationDuplicate(false);
    try {
      if (mode === "login") {
        const session = await signIn(trimmed, password);
        if (!session) throw new Error("Sign-in succeeded, but your account could not be loaded. Refresh to continue.");
        goTo(destinationAfterSignIn(session.role, window.location.search, window.location.origin, session.accountType));
        return;
      }
      if (mode === "register") {
        await register(trimmed, password);
        router.push(`/verify?email=${encodeURIComponent(trimmed)}`);
        return;
      } else if (mode === "resend") {
        await resendVerification(trimmed);
        router.push(`/verify?email=${encodeURIComponent(trimmed)}`);
        return;
      } else {
        const result = await requestPasswordReset(trimmed);
        setMessage(result.message ?? "If the account exists, password recovery instructions have been sent.");
        setRecoverySent(true);
      }
    } catch (cause) {
      if (mode === "register" && cause instanceof ApiRequestError && cause.status === 409) {
        setError("An account with this email already exists.");
        setRegistrationDuplicate(true);
      } else if (mode === "login" && cause instanceof ApiRequestError) {
        if (cause.status === 503) setError("Sign-in is temporarily unavailable. Please try again shortly.");
        else {
          if (cause.status === 401) setOfferResend(true);
          setError("Sign-in failed. Verify your email and password.");
        }
      } else {
        setError(cause instanceof Error ? cause.message : "Request failed. Try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  if (!sessionChecked) {
    return <StateMessage kind="loading" layout="inline" title="Checking your session…" />;
  }

  const resendOffer =
    mode === "login" && offerResend ? (
      <button
        type="button"
        onClick={() => switchMode("resend")}
        className="inline-flex min-h-10 items-center gap-1.5 rounded-sm text-data font-bold text-green-800 hover:underline"
      >
        Resend verification email
        <ArrowRight aria-hidden size={16} strokeWidth={1.8} />
      </button>
    ) : null;

  return (
    <div className="flex flex-col gap-6">
      {mode === "login" || mode === "register" ? (
        <SegmentedControl
          legend="Account access"
          value={mode}
          onChange={(value) => switchMode(value as Mode)}
          options={[
            { value: "login", label: "Sign in" },
            { value: "register", label: "Create account" },
          ]}
        />
      ) : null}

      <div>
        <h2 className="text-title text-ink">{headings[mode]}</h2>
        {mode === "register" ? (
          <p className="mt-1 text-data text-text-secondary">
            This creates a patient account for booking and managing your care. Doctors and other healthcare
            professionals should{" "}
            <Link href="/sign-up/provider" className="rounded-sm font-semibold text-green-800 underline-offset-2 hover:underline">
              sign up as a healthcare provider
            </Link>
            .
          </p>
        ) : null}
        {mode === "resend" ? (
          <p className="mt-1 text-data text-text-secondary">
            If this address has an account awaiting verification, we’ll send a fresh confirmation email.
          </p>
        ) : null}
        {mode === "forgot" ? (
          <p className="mt-1 text-data text-text-secondary">We’ll email a six-digit recovery code to this address.</p>
        ) : null}
      </div>

      <form noValidate onSubmit={submit} className="flex flex-col gap-5" aria-label={headings[mode]}>
        <TextInput
          ref={emailRef}
          name="email"
          type="email"
          label="Email address"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors.email}
        />
        {needsPassword ? (
          <PasswordField
            key={mode}
            ref={passwordRef}
            name="password"
            label="Password"
            required
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            description={mode === "register" ? "At least 12 characters." : undefined}
            error={fieldErrors.password}
          />
        ) : null}

        {error ? (
          <Alert
            tone="critical"
            live="assertive"
            title={error}
          >
            {registrationDuplicate
              ? "Sign in to your existing account, or reset your password if you can’t access it."
              : undefined}
          </Alert>
        ) : null}
        {registrationDuplicate ? (
          <div className="-mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-data font-semibold">
            <button
              type="button"
              onClick={() => switchMode("login")}
              className="min-h-10 rounded-sm text-green-800 hover:underline"
            >
              Sign in
            </button>
            <Link
              href={`/reset?email=${encodeURIComponent(email.trim())}`}
              className="min-h-10 content-center rounded-sm text-green-800 hover:underline"
            >
              Reset password
            </Link>
          </div>
        ) : null}
        {message ? <Alert tone="success" live="polite" title={message} /> : null}
        {googleStatusError && mode === "login" ? (
          <Alert tone="critical" live="polite" title={googleStatusError} />
        ) : null}
        {resendOffer}
        {recoverySent ? (
          <ButtonLink href={`/reset?email=${encodeURIComponent(email.trim())}`} variant="secondary" fullWidth>
            Enter your recovery code
          </ButtonLink>
        ) : null}

        <Button type="submit" fullWidth loading={busy}>
          {submitLabels[mode]}
        </Button>
      </form>

      <div className="flex flex-wrap items-center justify-between gap-2">
        {mode === "login" ? (
          <button
            type="button"
            onClick={() => switchMode("forgot")}
            className="min-h-10 rounded-sm text-data font-semibold text-green-800 hover:underline"
          >
            Forgot password?
          </button>
        ) : mode !== "register" ? (
          <button
            type="button"
            onClick={() => switchMode("login")}
            className="min-h-10 rounded-sm text-data font-semibold text-green-800 hover:underline"
          >
            Back to sign in
          </button>
        ) : null}
        {mode === "login" ? (
          <Link href="/reset" className="min-h-10 content-center rounded-sm text-data text-text-secondary hover:text-action">
            Have a recovery code?
          </Link>
        ) : null}
      </div>

      {mode === "login" && googleEnabled ? (
        <div className="border-t border-border pt-5">
          <p className="mb-3 text-caption font-semibold text-text-secondary">Or continue with</p>
          <a
            className={buttonClasses({ variant: "secondary", fullWidth: true })}
            href="/oauth2/authorization/google"
          >
            Continue with Google
          </a>
        </div>
      ) : null}

      <div className="border-t border-border pt-5">
        <p className="mt-3 text-caption text-text-muted">
          MedNet secures your account and keeps access roles and protected data on its backend.
        </p>
      </div>
    </div>
  );
}
