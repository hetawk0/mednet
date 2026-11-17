"use client";

import { UserRound } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { WorkspaceShell } from "@/components/layout/workspace-shell";
import { Alert } from "@/components/ui/alert";
import { Menu } from "@/components/ui/menu";
import { ApiRequestError, getSession, signOut, type Session } from "@/lib/api";
import { providerApi } from "@/features/provider/provider-api";
import { WORKSPACES, activeNavItemId, homePathForSession } from "@/features/workspace/workspaces";
import { formatHeaderDate, greeting } from "./format";
import { SESSION_EXPIRED_EVENT } from "@/features/workspace/use-resource";
import { WorkspaceAccessState } from "@/features/workspace/access-state";
import { patientApi, type PatientProfile } from "./patient-api";

/**
 * Patient workspace frame: session guard, profile context and the shared WorkspaceShell.
 *
 * The guard is UX only — it avoids showing a patient frame to the wrong account. Every request
 * below it is authorised again by Spring (`hasRole('PATIENT')` plus ownership checks).
 */

export type ProfileState =
  | { state: "loading" }
  | { state: "ready"; profile: PatientProfile }
  /** 404: the patient has not completed their profile (required before booking). */
  | { state: "missing" }
  | { state: "error" };

type PatientContextValue = {
  session: Session;
  profile: ProfileState;
  setProfile: (profile: PatientProfile) => void;
};

const PatientContext = createContext<PatientContextValue | null>(null);

export function usePatient(): PatientContextValue {
  const value = useContext(PatientContext);
  if (!value) throw new Error("usePatient must be used inside PatientWorkspace");
  return value;
}

type Access =
  | { state: "checking" }
  | { state: "signed-out" }
  /** A request answered 401 after the workspace loaded. */
  | { state: "expired" }
  | { state: "error" }
  | { state: "redirecting" }
  | { state: "ready"; session: Session };

const workspace = WORKSPACES.patient;

/** Top-bar title for the current route; the overview greets the patient instead. */
function pageTitle(pathname: string): string {
  if (pathname === "/patient/profile" || pathname.startsWith("/patient/profile/")) return "Your profile";
  if (pathname === "/patient/appointments/consultation") return "Text consultation";
  const id = activeNavItemId(workspace, pathname);
  return [...workspace.items, ...(workspace.footerItems ?? [])].find((item) => item.id === id)?.label ?? "Patient workspace";
}

export function PatientWorkspace({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [access, setAccess] = useState<Access>({ state: "checking" });
  const [profile, setProfileState] = useState<ProfileState>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [signOutFailed, setSignOutFailed] = useState(false);
  const [now, setNow] = useState<Date | null>(null);
  /** Patients who applied to provide care keep the PATIENT role until approval (SecurityConfig). */
  const [hasProviderApplication, setHasProviderApplication] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    getSession(signal)
      .then((session) => {
        if (signal.aborted) return;
        if (!session) return setAccess({ state: "signed-out" });
        // Other roles, and provider sign-ups still in onboarding (PATIENT role until approved), go home.
        const home = homePathForSession(session);
        if (home !== "/patient") {
          setAccess({ state: "redirecting" });
          router.replace(home);
          return;
        }
        setAccess({ state: "ready", session });
        patientApi
          .profile(signal)
          .then((saved) => setProfileState({ state: "ready", profile: saved }))
          .catch((error) => {
            if (signal.aborted) return;
            setProfileState(error instanceof ApiRequestError && error.status === 404 ? { state: "missing" } : { state: "error" });
          });
        // 404 (never applied) or any failure simply hides the link; the status page itself is
        // read-only and the API decides what an applicant may see.
        providerApi
          .application(signal)
          .then(() => !signal.aborted && setHasProviderApplication(true))
          .catch(() => undefined);
      })
      .catch(() => !signal.aborted && setAccess({ state: "error" }));
    return () => controller.abort();
  }, [router, attempt]);

  useEffect(() => {
    const expire = () => setAccess({ state: "expired" });
    window.addEventListener(SESSION_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expire);
  }, []);

  // The date and greeting depend on the viewer's clock, so they render after hydration.
  useEffect(() => {
    const tick = () => setNow(new Date());
    tick();
    const timer = window.setInterval(tick, 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const setProfile = useCallback((saved: PatientProfile) => setProfileState({ state: "ready", profile: saved }), []);

  if (access.state !== "ready") {
    return (
      <WorkspaceAccessState
        access={access}
        pathname={pathname}
        workspaceLabel="Patient workspace"
        onRetry={() => setAttempt((count) => count + 1)}
      />
    );
  }

  const fullName = profile.state === "ready" ? profile.profile.fullName : null;
  const overview = pathname === "/patient";

  async function endSession() {
    setSignOutFailed(false);
    try {
      await signOut();
      router.replace("/sign-in");
    } catch {
      setSignOutFailed(true);
    }
  }

  return (
    <PatientContext.Provider value={{ session: access.session, profile, setProfile }}>
      <WorkspaceShell
        workspaceId="patient"
        user={{ name: fullName || access.session.email, description: "Patient account" }}
        eyebrow={overview ? (now ? formatHeaderDate(now) : " ") : "Patient workspace"}
        title={overview ? (now ? greeting(fullName, now) : "Welcome") : pageTitle(pathname)}
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
              { id: "profile", label: "Your profile", onSelect: () => router.push("/patient/profile") },
              ...(hasProviderApplication
                ? [{ id: "provider-application", label: "Provider application status", onSelect: () => router.push("/provider") }]
                : []),
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
          {children}
        </div>
      </WorkspaceShell>
    </PatientContext.Provider>
  );
}
