"use client";

import { UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Avatar } from "@/components/ui/avatar";
import { Menu } from "@/components/ui/menu";
import { homePathForSession } from "@/features/workspace/workspaces";
import { getSession, signOut, type Session } from "@/lib/api";

const roleDescriptions: Record<string, string> = {
  PATIENT: "Patient account",
  PROVIDER: "Provider account",
  LABORATORY: "Laboratory partner",
  HOME_CARE: "Home care partner",
  ADMIN: "Administrator",
  SUPER_ADMIN: "Super administrator",
};

type Status = "loading" | "signed-out" | "signed-in";

/**
 * Session-aware actions for the public site header: Sign in when anonymous, otherwise the same
 * account affordance pattern as workspace shells (avatar + menu).
 */
export function PublicAccountMenu({ layout }: { layout: "bar" | "mobile" }) {
  const router = useRouter();
  const [status, setStatus] = useState<Status>("loading");
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    let active = true;
    getSession()
      .then((current) => {
        if (!active) return;
        if (current) {
          setSession(current);
          setStatus("signed-in");
        } else {
          setStatus("signed-out");
        }
      })
      .catch(() => {
        if (active) setStatus("signed-out");
      });
    return () => {
      active = false;
    };
  }, []);

  async function endSession() {
    try {
      await signOut();
      setSession(null);
      setStatus("signed-out");
      router.refresh();
    } catch {
      window.location.href = "/sign-in";
    }
  }

  if (status === "loading") {
    return layout === "bar" ? (
      <span className="hidden min-h-11 min-w-[5.5rem] md:inline-flex" aria-hidden />
    ) : null;
  }

  if (status === "signed-out") {
    if (layout === "mobile") {
      return (
        <li>
          <Link
            href="/sign-in"
            className="flex min-h-11 items-center border-b border-line-soft text-body font-semibold text-ink"
          >
            Sign in
          </Link>
        </li>
      );
    }
    return (
      <Link
        href="/sign-in"
        className="hidden min-h-11 items-center rounded-sm px-5 text-button font-bold text-green-800 hover:bg-action-subtle md:inline-flex"
      >
        Sign in
      </Link>
    );
  }

  const workspace = homePathForSession(session!);
  const description = roleDescriptions[session!.role] ?? session!.role;

  if (layout === "mobile") {
    return (
      <li>
        <Link
          href={workspace}
          className="flex min-h-11 items-center border-b border-line-soft text-body font-semibold text-ink"
        >
          Your workspace
        </Link>
      </li>
    );
  }

  return (
    <div className="hidden items-center gap-3 md:flex">
      <div className="flex items-center gap-2.5">
        <Avatar name={session!.email} size="sm" />
        <span className="hidden max-w-[11rem] flex-col lg:flex">
          <span className="truncate text-caption font-semibold text-ink">{session!.email}</span>
          <span className="truncate text-caption text-text-secondary">{description}</span>
        </span>
      </div>
      <Menu
        label={
          <>
            <UserRound aria-hidden size={18} strokeWidth={1.8} className="lg:hidden" />
            <span className="sr-only lg:not-sr-only">Account</span>
          </>
        }
        align="end"
        items={[
          { id: "workspace", label: "Open workspace", onSelect: () => router.push(workspace) },
          { id: "sign-out", label: "Sign out", onSelect: () => void endSession(), tone: "critical" },
        ]}
      />
    </div>
  );
}