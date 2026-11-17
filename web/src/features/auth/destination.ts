import { homePathForSession } from "@/features/workspace/workspaces";

/**
 * Where to send a user after sign-in: the `?redirect=` target when it is a safe same-origin
 * path, otherwise the role's home. Behaviour is unchanged from the original sign-in page:
 * protocol-relative URLs, backslashes, other origins and /sign-in itself are rejected.
 */
export function safeDestination(redirect: string | null, fallback: string, origin: string): string {
  if (!redirect?.startsWith("/") || redirect.startsWith("//") || redirect.includes("\\")) {
    return fallback;
  }
  let requested: URL;
  try {
    requested = new URL(redirect, origin);
  } catch {
    return fallback;
  }
  if (requested.origin !== origin || requested.pathname === "/sign-in") return fallback;
  return `${requested.pathname}${requested.search}${requested.hash}`;
}

/** `accountType` (from the session) only chooses the landing page; it never grants access. */
export function destinationAfterSignIn(
  role: string,
  search: string,
  origin: string,
  accountType?: string | null,
): string {
  const redirect = new URLSearchParams(search).get("redirect");
  return safeDestination(redirect, homePathForSession({ role, accountType }), origin);
}
