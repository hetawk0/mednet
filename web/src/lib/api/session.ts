import { apiRequest } from "./client";
import { ApiRequestError } from "./errors";

/** Roles issued by the backend today (see `hasRole` checks in the Spring controllers). */
export const BACKEND_ROLES = [
  "PATIENT",
  "PROVIDER",
  "LABORATORY",
  "HOME_CARE",
  "ADMIN",
  "SUPER_ADMIN",
] as const;

export type BackendRole = (typeof BACKEND_ROLES)[number];

/**
 * `role` is the effective role the API enforces. `accountType` is the stored account type (null without an
 * account record): a PROVIDER account type with the PATIENT role is an applicant awaiting approval. Use it
 * for routing only — access is always decided by `role` on the server.
 */
export type Session = { email: string; role: BackendRole | (string & {}); accountType?: string | null };

/** Returns the signed-in session, or null when there is none. Other failures throw. */
export async function getSession(signal?: AbortSignal): Promise<Session | null> {
  try {
    return await apiRequest<Session>("/auth/session", { signal });
  } catch (error) {
    if (error instanceof ApiRequestError && error.isUnauthenticated) return null;
    throw error;
  }
}

/** Ends the current MedNet session. */
export async function signOut(): Promise<void> {
  await apiRequest<void>("/auth/logout", { method: "POST" });
}
