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
import { homeCareApi, type HomeCareRequest } from "./home-care-api";

/**
 * Home-care workspace frame (Phase 5C): session guard (HOME_CARE only), overview counts and WorkspaceShell.
 * Queue pages load their own server-filtered page; the API decides which requests this account may see or change.
 * Request data lives only in React state; nothing is written to browser storage.
 */
type HomeCareContextValue = {
  session: Session;
  requests: Resource<HomeCareRequest[]>;
  reloadRequests: () => void;
  replaceRequest: (request: HomeCareRequest) => void;
};

const HomeCareContext = createContext<HomeCareContextValue | null>(null);

export function useHomeCare(): HomeCareContextValue {
  const value = useContext(HomeCareContext);
  if (!value) throw new Error("useHomeCare must be used inside HomeCareWorkspace");
  return value;
}

function pageTitle(pathname: string): string {
  if (pathname === "/home-care/profile") return "Your account";
  return "Home care";
}

export function HomeCareWorkspace({ children }: { children: ReactNode }) {
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
        if (session.role !== "HOME_CARE") {
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
        workspaceLabel="Home-care workspace"
        onRetry={() => setAttempt((count) => count + 1)}
      />
    );
  }

  return (
    <WorkspaceShell
      workspaceId="home-care"
      user={{ name: access.session.email, description: "Home-care partner" }}
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
            { id: "account", label: "Your account", onSelect: () => router.push("/home-care/profile") },
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
        {/* Mounted only for a HOME_CARE session, so no request is fetched for anyone else. */}
        <HomeCareData session={access.session} loadOverviewRequests={pathname === "/home-care"}>
          {children}
        </HomeCareData>
      </div>
    </WorkspaceShell>
  );
}

function HomeCareData({
  session,
  children,
  loadOverviewRequests,
}: {
  session: Session;
  children: ReactNode;
  loadOverviewRequests: boolean;
}) {
  const { resource, reload, replace } = useResource(
    (signal) => loadOverviewRequests ? homeCareApi.requests(signal) : Promise.resolve([]),
    `home-care-requests-${loadOverviewRequests}`,
  );
  const replaceRequest = useCallback(
    (request: HomeCareRequest) => {
      if (resource.state === "ready") replace(resource.data.map((row) => (row.id === request.id ? request : row)));
    },
    [resource, replace],
  );
  return (
    <HomeCareContext.Provider value={{ session, requests: resource, reloadRequests: reload, replaceRequest }}>
      {children}
    </HomeCareContext.Provider>
  );
}
