"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import {
  WORKSPACES,
  activeNavItemId,
  type WorkspaceId,
  type WorkspaceNavItem,
} from "@/features/workspace/workspaces";
import { Avatar } from "@/components/ui/avatar";
import { cn } from "@/lib/cn";
import { Wordmark } from "./wordmark";

export type WorkspaceUser = {
  name: string;
  /** e.g. "Patient account" */
  description: string;
};

type WorkspaceShellProps = {
  /** Looked up here (client side) because nav icons are components and cannot cross the server boundary. */
  workspaceId: WorkspaceId;
  user: WorkspaceUser;
  /** Small line above the title, e.g. the date or "Current workspace". */
  eyebrow?: string;
  title: string;
  /** Top-bar controls (search, messages, fictional-data badge…). */
  actions?: ReactNode;
  /** Full-width notice above the top bar that stays visible while scrolling (preview workspaces). */
  banner?: ReactNode;
  children: ReactNode;
};

/**
 * Reference: Navigation/Dashboard sidebar + workspace top bar, shared by patient, doctor
 * and operations workspaces. Sidebar is a rail below 1200px and full width from 1200px.
 * It is sticky and fills the viewport height (the reference's background stops at 900px).
 */
export function WorkspaceShell({
  workspaceId,
  user,
  eyebrow,
  title,
  actions,
  banner,
  children,
}: WorkspaceShellProps) {
  const pathname = usePathname();
  const workspace = WORKSPACES[workspaceId];
  const activeId = activeNavItemId(workspace, pathname);

  return (
    <div className="grid min-h-dvh grid-cols-[var(--width-sidebar-rail)_minmax(0,1fr)] bg-canvas lg:grid-cols-[var(--width-sidebar)_minmax(0,1fr)]">
      <a
        href="#workspace-content"
        className="sr-only z-50 rounded-sm bg-surface px-4 py-3 text-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>

      <aside className="sticky top-0 z-20 flex h-dvh flex-col bg-sidebar px-3 py-6 text-white lg:overflow-y-auto lg:px-5 [&_:focus-visible]:outline-green-100">
        <div className="px-1 lg:px-2">
          <span className="lg:hidden">
            <Wordmark tone="light" compact />
          </span>
          <span className="hidden lg:inline">
            <Wordmark tone="light" size="lg" />
          </span>
        </div>

        <p className="mt-8 hidden px-2 text-eyebrow font-semibold text-green-100 uppercase lg:block">
          {workspace.label}
        </p>

        <nav aria-label={workspace.label} className="mt-6 lg:mt-3">
          <NavList
            items={workspace.items}
            activeId={activeId}
            numbered={workspace.navStyle === "numbered"}
          />
        </nav>

        <div className="mt-auto border-t border-white/15 pt-4">
          {workspace.footerItems ? (
            <NavList items={workspace.footerItems} activeId={activeId} numbered={false} />
          ) : (
            <UserBadge user={user} tone="light" />
          )}
        </div>
      </aside>

      <div className="flex min-w-0 flex-col">
        {banner ? <div className="sticky top-0 z-10">{banner}</div> : null}
        <header className="flex min-h-[4.75rem] items-center gap-4 border-b border-border bg-surface px-4 md:px-6 lg:px-8">
          <div className="min-w-0">
            {eyebrow ? (
              <p className="text-caption text-text-secondary [overflow-wrap:anywhere] md:truncate">{eyebrow}</p>
            ) : null}
            <h1 title={title} className="text-xl font-semibold text-ink [overflow-wrap:anywhere] md:truncate md:text-[1.375rem]">
              {title}
            </h1>
          </div>
          <div className="ml-auto flex shrink-0 items-center gap-3">
            {actions}
            {workspace.footerItems ? (
              <div className="flex items-center gap-3 md:border-l md:border-border md:pl-4">
                <UserBadge user={user} tone="dark" />
              </div>
            ) : null}
          </div>
        </header>

        <main id="workspace-content" className="min-w-0 flex-1 px-4 py-6 md:px-6 md:py-8 lg:px-8">
          {children}
        </main>
      </div>
    </div>
  );
}

function NavList({
  items,
  activeId,
  numbered,
}: {
  items: WorkspaceNavItem[];
  activeId?: string;
  numbered: boolean;
}) {
  return (
    <ul className="flex flex-col gap-1">
      {items.map((item, index) => {
        const active = item.id === activeId;
        const Icon = item.icon;
        return (
          <li key={item.id}>
            <Link
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group relative flex min-h-11 items-center justify-center gap-3 rounded-sm px-2 text-body text-white/85 hover:bg-white/10 hover:text-white lg:justify-start lg:px-3",
                active &&
                  "bg-white/10 font-semibold text-white before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-green-100",
              )}
            >
              {numbered ? (
                <span
                  aria-hidden
                  className="inline-flex size-7 shrink-0 items-center justify-center rounded-sm bg-white/10 text-eyebrow font-semibold tabular-nums"
                >
                  {String(index + 1).padStart(2, "0")}
                </span>
              ) : Icon ? (
                <Icon aria-hidden size={20} strokeWidth={1.8} className="shrink-0" />
              ) : null}
              <span className="sr-only lg:not-sr-only">{item.label}</span>
              {/* Rail (<1200px): visible label on hover or keyboard focus; decorative, the sr-only text names the link. */}
              <span
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-full z-30 ml-3 hidden -translate-y-1/2 rounded-sm bg-green-950 px-3 py-1.5 text-caption font-semibold whitespace-nowrap text-white shadow-overlay ring-1 ring-white/15 group-hover:block group-focus-visible:block lg:hidden!"
              >
                {item.label}
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function UserBadge({ user, tone }: { user: WorkspaceUser; tone: "light" | "dark" }) {
  return (
    <div className="flex items-center gap-3">
      <Avatar name={user.name} />
      <span
        className={cn(
          "min-w-0 flex-col",
          tone === "light" ? "hidden lg:flex" : "hidden md:flex",
        )}
      >
        <span
          className={cn(
            "truncate text-data font-semibold",
            tone === "light" ? "text-white" : "text-ink",
          )}
        >
          {user.name}
        </span>
        <span
          className={cn(
            "truncate text-caption",
            tone === "light" ? "text-green-100" : "text-text-secondary",
          )}
        >
          {user.description}
        </span>
      </span>
    </div>
  );
}
