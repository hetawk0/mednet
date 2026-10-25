"use client";

import { UserRound } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { Alert } from "@/components/ui/alert";
import { Menu } from "@/components/ui/menu";
import { getSession, signOut, type Session } from "@/lib/api";
import { WorkspaceAccessState, type WorkspaceAccess } from "@/features/workspace/access-state";
import { SESSION_EXPIRED_EVENT, useResource, type Resource } from "@/features/workspace/use-resource";
import { homePathForRole } from "@/features/workspace/workspaces";
import { labApi, type LabRequest, type LatestResult, type SubmittedResult } from "./lab-api";

/**
 * Laboratory workspace frame: session guard (LABORATORY only), overview/result lookup data and WorkspaceShell.
 * The order queue loads its own server-filtered page; the API decides which requests this account may see or change.
 *
 * Each order arrives with its latest result's review state (`latestResult`, Phase 5D), so the queue shows
 * whether a result awaits doctor review, was returned or was released without loading the result list.
 */
type LaboratoryContextValue = {
  session: Session;
  requests: Resource<LabRequest[]>;
  reloadRequests: () => void;
  replaceRequest: (request: LabRequest) => void;
  /** Newest submission's review state for a request, if any. */
  latestResult: (requestId: string) => LatestResult | null | undefined;
  /** Records a just-submitted result on its order. */
  addResult: (result: SubmittedResult) => void;
};

const LaboratoryContext = createContext<LaboratoryContextValue | null>(null);

export function useLaboratory(): LaboratoryContextValue {
  const value = useContext(LaboratoryContext);
  if (!value) throw new Error("useLaboratory must be used inside LaboratoryWorkspace");
  return value;
}

function pageTitle(pathname: string): string {
  if (pathname === "/laboratory/profile") return "Your account";
  return "Laboratory";
}

export function LaboratoryWorkspace({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [access, setAccess] = useState<WorkspaceAccess | { state: "ready"; session: Session }>({ state: "checking" });
  const [attempt, setAttempt] = useState(0);
  const [signOutFailed, setSignOutFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    getSession(signal)
      .then((session) => {
        if (signal.aborted) return;
        if (!session) return setAccess({ state: "signed-out" });
        if (session.role !== "LABORATORY") {
          setAccess({ state: "redirecting" });
          router.replace(homePathForRole(session.role));
          return;
        }
        setAccess({ state: "ready", session });
      })
      .catch(() => !signal.aborted && setAccess({ state: "error" }));
    return () => controller.abort();
  }, [router, attempt]);

  useEffect(() => {
    const expire = () => setAccess({ state: "expired" });
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, []);

  async function endSession() {
    setSignOutFailed(false);
    try {
      await signOut();
      router.replace("/sign-in");
    } catch {
      setSignOutFailed(true);
    }
  }

  if (access.state !== "ready") {
    return (
      <WorkspaceAccessState
        access={access}
        pathname={pathname}
        workspaceLabel="Laboratory workspace"
        onRetry={() => setAttempt((count) => count + 1)}
      />
    );
  }

  return (
    <WorkspaceShell
      workspaceId="laboratory"
      user={{ name: access.session.email, description: "Laboratory partner" }}
      eyebrow="Current workspace"
      title={pageTitle(pathname)}
      actions={
        <Menu
          label={
            <>
              <UserRound aria-hidden size={18} strokeWidth={1.8} className="md:hidden" />
              <span className="sr-only md:not-sr-only">Account</span>
            </>
          }
          align="end"
          items={[
            { id: "account", label: "Your account", onSelect: () => router.push("/laboratory/profile") },
            { id: "sign-out", label: "Sign out", onSelect: () => void endSession() },
          ]}
        />
      }
    >
      <div className="mx-auto flex max-w-[80rem] flex-col gap-6">
        {signOutFailed ? (
          <Alert tone="critical" live="assertive" title="Sign-out failed">
            Close this browser session and try again.
          </Alert>
        ) : null}
        {/* Mounted only for a LABORATORY session, so no request is fetched for anyone else. */}
        <LaboratoryData
          session={access.session}
          loadRequestList={pathname === "/laboratory" || pathname === "/laboratory/results"}
        >
          {children}
        </LaboratoryData>
      </div>
    </WorkspaceShell>
  );
}

function LaboratoryData({
  session,
  children,
  loadRequestList,
}: {
  session: Session;
  children: ReactNode;
  loadRequestList: boolean;
}) {
  const { resource, reload, replace } = useResource(
    (signal) => loadRequestList ? labApi.requests(signal) : Promise.resolve([]),
    `lab-requests-${loadRequestList}`,
  );

  const replaceRequest = useCallback(
    (request: LabRequest) => {
      if (resource.state !== "ready") return;
      // A status change answers without latestResult; keep the one already shown.
      replace(resource.data.map((row) => (row.id === request.id ? { ...request, latestResult: request.latestResult ?? row.latestResult } : row)));
    },
    [resource, replace],
  );
  const latestResult = useCallback(
    (requestId: string) => (resource.state === "ready" ? resource.data.find((row) => row.id === requestId)?.latestResult : undefined),
    [resource],
  );
  const addResult = useCallback(
    (result: SubmittedResult) => {
      if (resource.state !== "ready") return;
      const latest: LatestResult = { id: result.id, status: result.status, createdAt: result.createdAt, reviewedAt: result.reviewedAt };
      replace(resource.data.map((row) => (row.id === result.requestId ? { ...row, latestResult: latest } : row)));
    },
    [resource, replace],
  );

  return (
    <LaboratoryContext.Provider
      value={{
        session,
        requests: resource,
        reloadRequests: reload,
        replaceRequest,
        latestResult,
        addResult,
      }}
    >
      {children}
    </LaboratoryContext.Provider>
  );
}
