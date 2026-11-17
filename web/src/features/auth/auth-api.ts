import { apiRequest } from "@/lib/api/client";
import { getSession, type Session } from "@/lib/api/session";

type MessageResponse = { message?: string };

export async function googleAuthEnabled(): Promise<boolean> {
  const result = await apiRequest<{ enabled?: boolean }>("/auth/google/status");
  return result.enabled === true;
}

/** Authenticate with the MedNet API's secure HTTP session. */
export async function signIn(email: string, password: string): Promise<Session | null> {
  await apiRequest<void>("/auth/login", {
    method: "POST",
    form: { email, password },
  });
  return getSession();
}

/**
 * Register a MedNet account and send its verification code through EKDSend. Patient sign-up omits the
 * account type (the API defaults to PATIENT); provider sign-up sends PROVIDER. A PROVIDER account still
 * signs in with patient access until an administrator approves its provider application.
 */
export async function register(
  email: string,
  password: string,
  accountType?: "PROVIDER",
): Promise<MessageResponse> {
  return apiRequest<MessageResponse>("/auth/register", {
    method: "POST",
    body: accountType ? { email, password, accountType } : { email, password },
  });
}

export async function resendVerification(email: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>("/auth/resend-verification", {
    method: "POST",
    body: { email },
  });
}

export async function requestPasswordReset(email: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>("/auth/forgot-password", {
    method: "POST",
    body: { email },
  });
}

export async function verifyResetCode(email: string, code: string): Promise<string> {
  const result = await apiRequest<{ token?: string }>("/auth/reset-password/verify", {
    method: "POST",
    body: { email, code },
  });
  if (!result.token) throw new Error("Could not verify that code.");
  return result.token;
}

export async function verifyEmailCode(email: string, code: string): Promise<void> {
  await apiRequest<MessageResponse>("/auth/verify-code", {
    method: "POST",
    body: { email, code },
  });
}

export async function verifyEmailLink(email: string, token: string): Promise<void> {
  await apiRequest<MessageResponse>("/auth/verify", {
    query: { email, token },
  });
}

export async function resetPassword(email: string, token: string, password: string): Promise<MessageResponse> {
  return apiRequest<MessageResponse>("/auth/reset-password", {
    method: "POST",
    body: { email, token, password },
  });
}

export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_BYTES = 72;

export function passwordProblem(password: string): string | null {
  if (password.length < PASSWORD_MIN_LENGTH) return `Use at least ${PASSWORD_MIN_LENGTH} characters.`;
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) return "Use a shorter password (72 bytes maximum).";
  return null;
}

export function emailProblem(email: string): string | null {
  if (!email.trim()) return "Enter your email address.";
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return "Enter a valid email address.";
  return null;
}
