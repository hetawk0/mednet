"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink } from "@/components/ui/button";
import { TextInput } from "@/components/ui/inputs";
import { StateMessage } from "@/components/ui/state-message";
import { emailProblem, resendVerification, verifyEmailCode, verifyEmailLink } from "./auth-api";

type State = { kind: "loading" } | { kind: "form" } | { kind: "success" };

/** Accepts the emailed OTP by default and supports callback-link verification as a fallback. */
export function VerifyEmailPanel() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [resendMessage, setResendMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    let active = true;
    const queryEmail = params.get("email") ?? "";
    const legacyToken = params.get("token");
    const initialize = async () => {
      setEmail(queryEmail);
      if (legacyToken) {
        try {
          await verifyEmailLink(queryEmail, legacyToken);
          if (active) setState({ kind: "success" });
          return;
        } catch (cause) {
          if (active) {
            setError(cause instanceof Error ? cause.message : "This verification link is invalid or expired.");
          }
        }
      }
      if (active) setState({ kind: "form" });
    };
    void initialize();
    return () => {
      active = false;
    };
  }, []);

  async function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const emailError = emailProblem(email.trim());
    if (emailError) return setError(emailError);
    if (!/^\d{6}$/.test(code)) return setError("Enter the six-digit code from your email.");
    setBusy(true);
    setError("");
    try {
      await verifyEmailCode(email.trim(), code);
      setState({ kind: "success" });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The code is invalid or expired. Request a new email.");
    } finally {
      setBusy(false);
    }
  }

  async function resendCode() {
    const emailError = emailProblem(email.trim());
    if (emailError) return setError(emailError);
    setResending(true);
    setError("");
    setResendMessage("");
    try {
      const response = await resendVerification(email.trim());
      setResendMessage(
        response.message ??
          "If this email needs verification, a new six-digit code and secure link will arrive shortly.",
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "The verification email could not be sent. Try again shortly.");
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <h2 className="text-title text-ink">Email verification</h2>
      {state.kind === "loading" ? (
        <StateMessage kind="loading" layout="inline" title="Preparing verification…" />
      ) : state.kind === "success" ? (
        <StateMessage
          kind="success"
          title="Email verified"
          description="Your email address is confirmed. Sign in to continue; provider accounts continue with their application."
        />
      ) : (
        <>
          <p className="text-data text-text-secondary">
            Enter the six-digit code from your MedNet email. If the code does not work, use the secure verification link included in that email.
          </p>
          {error ? <Alert tone="critical" live="assertive" title={error} /> : null}
          <form noValidate onSubmit={submitCode} className="flex flex-col gap-5" aria-label="Verify your email">
            <TextInput
              label="Email address"
              name="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <TextInput
              label="Six-digit verification code"
              name="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              maxLength={6}
              pattern="[0-9]{6}"
              required
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            />
            <Button type="submit" fullWidth loading={busy}>Verify email</Button>
          </form>
          {resendMessage ? (
            <Alert tone="success" live="polite" title={resendMessage}>
              Check your inbox and spam folder. If you still do not receive it, verify the email address above.
            </Alert>
          ) : null}
          <Button
            type="button"
            variant="secondary"
            fullWidth
            loading={resending}
            disabled={busy || emailProblem(email.trim()) !== null}
            onClick={() => void resendCode()}
          >
            Resend code and secure link
          </Button>
        </>
      )}
      {state.kind === "success" ? (
        <ButtonLink href="/sign-in" fullWidth>Continue to sign in</ButtonLink>
      ) : state.kind !== "loading" ? (
        <ButtonLink href="/sign-in" fullWidth variant="secondary">Back to sign in</ButtonLink>
      ) : null}
    </div>
  );
}
