"use client";

import { useSyncExternalStore, type ReactNode } from "react";
import { Wordmark } from "@/components/layout/wordmark";
import { Button, ButtonLink } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";

/** Session states every signed-in workspace handles before (or instead of) showing its shell. */
export type WorkspaceAccess =
  | { state: "checking" }
  | { state: "signed-out" }
  /** A request answered 401 after the workspace loaded. */
  | { state: "expired" }
  | { state: "error" }
  | { state: "redirecting" };

/**
 * Full-page access states without the workspace shell, so no workspace data or navigation is shown.
 * Sign-in links keep the current path and query so the person returns to exactly this view.
 */
export function WorkspaceAccessState({
  access,
  pathname,
  workspaceLabel,
  onRetry,
  children,
}: {
  access: WorkspaceAccess;
  pathname: string;
  /** e.g. "Patient workspace" */
  workspaceLabel: string;
  onRetry: () => void;
  /** Replaces the default content (used for account states such as an unapproved provider). */
  children?: ReactNode;
}) {
  const search = useSyncExternalStore(
    () => () => undefined,
    () => window.location.search,
    () => "",
  );
  const signIn = `/sign-in?redirect=${encodeURIComponent(`${pathname}${search}`)}`;
  const content =
    children ??
    (access.state === "expired" ? (
      <StateMessage
        kind="denied"
        live
        title="Your session has ended"
        description="For your privacy, your information is no longer shown. Sign in again to continue where you left off."
        action={<ButtonLink href={signIn}>Sign in again</ButtonLink>}
      />
    ) : access.state === "signed-out" ? (
      <StateMessage
        kind="denied"
        live
        title="Sign in to continue"
        description={`Your ${workspaceLabel.toLowerCase()} is available after you sign in.`}
        action={<ButtonLink href={signIn}>Go to sign in</ButtonLink>}
      />
    ) : access.state === "error" ? (
      <StateMessage
        kind="error"
        title="The MedNet API is unavailable"
        description="Your session could not be checked. Try again shortly."
        action={
          <Button variant="secondary" size="sm" onClick={onRetry}>
            Try again
          </Button>
        }
      />
    ) : (
      <div aria-busy className="flex flex-col gap-4">
        <span role="status" className="sr-only">
          {access.state === "redirecting" ? "Opening your workspace…" : "Checking your session…"}
        </span>
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    ));

  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex h-16 max-w-page items-center px-4 md:px-6 lg:px-8">
          <Wordmark size="sm" />
        </div>
      </header>
      <main id="main-content" className="mx-auto w-full max-w-2xl flex-1 px-4 py-12 md:py-16">
        <h1 className="sr-only">{workspaceLabel}</h1>
        {content}
      </main>
    </div>
  );
}
