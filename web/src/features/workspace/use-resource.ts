"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";

/**
 * Fired when the API answers 401 (the session expired or was ended elsewhere). PatientWorkspace
 * listens and replaces the workspace — and every piece of patient data held in memory — with a
 * sign-in prompt that returns to the same page.
 */
export const SESSION_EXPIRED_EVENT = "mednet:session-expired";

export function reportSessionExpired() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

export type Resource<T> =
  | { state: "loading" }
  | { state: "ready"; data: T }
  /** `status` is the HTTP status (0 for a network failure) so views can tell 403 from an outage. */
  | { state: "error"; status: number; message: string };

/**
 * Loads one API resource for a client view, aborting on unmount. `reload()` refetches without
 * blanking the current data; `replace()` applies a server response from a mutation.
 */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>, key: string = "") {
  const [snapshot, setSnapshot] = useState<{ key: string; resource: Resource<T> }>({
    key,
    resource: { state: "loading" },
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal)
      .then((data) => !controller.signal.aborted && setSnapshot({ key, resource: { state: "ready", data } }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiRequestError && error.isUnauthenticated) reportSessionExpired();
        setSnapshot({
          key,
          resource: {
            state: "error",
            status: error instanceof ApiRequestError ? error.status : 0,
            message: error instanceof Error ? error.message : "The request failed.",
          },
        });
      });
    return () => controller.abort();
    // `load` is recreated each render by callers; `key` and `attempt` decide when to refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt]);

  const reload = useCallback(() => setAttempt((count) => count + 1), []);
  const replace = useCallback(
    (data: T) => setSnapshot({ key, resource: { state: "ready", data } }),
    [key],
  );
  const resource = snapshot.key === key ? snapshot.resource : { state: "loading" as const };
  return { resource, reload, replace };
}

/** Plain-language message for a failed action, keeping the API's own message when it has one. */
export function actionError(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    if (error.isUnauthenticated) {
      reportSessionExpired();
      return "Your session has ended. Sign in again to continue.";
    }
    if (error.isForbidden) return error.message || "You do not have permission to do this.";
    return error.message || fallback;
  }
  return fallback;
}
