import {
  ArrowRight,
  CalendarClock,
  CalendarDays,
  Heart,
  HeartPulse,
  House,
  MessageSquareText,
  ReceiptText,
  ShieldCheck,
  Stethoscope,
  Users,
  WalletCards,
  type LucideIcon,
} from "lucide-react";
import type { BackendRole } from "@/lib/api";

/**
 * Workspace registry — one entry per workspace in the approved Figma reference, plus home care
 * (Phase 5C), which the reference does not design.
 *
 * `backend` records what the Spring Boot API supports today, so screens can show
 * live data, clearly labelled fictional data, or nothing. It never grants access:
 * the backend authorizes every request independently.
 *
 * - `supported`: endpoints exist for this view.
 * - `partial`:   some endpoints exist; the design goes further.
 * - `none`:      no backend capability (or out of scope, e.g. payments). Presentation-only.
 */
export type BackendSupport = "supported" | "partial" | "none";

export type WorkspaceId =
  | "patient"
  | "doctor"
  | "laboratory"
  | "home-care"
  | "collection-provider"
  | "collection-agent"
  | "super-admin"
  | "admin"
  | "support"
  | "finance"
  | "specimen-lifecycle"
  | "permissions-matrix";

export type WorkspaceNavItem = {
  id: string;
  label: string;
  href: string;
  backend: BackendSupport;
  /** Icon-style navigation only; numbered navigation renders its position. */
  icon?: LucideIcon;
};

export type WorkspaceDefinition = {
  id: WorkspaceId;
  /** Label shown in the sidebar, e.g. "Patient workspace". */
  label: string;
  /** Short name used in the top bar, e.g. "Administrator". */
  name: string;
  navStyle: "icon" | "numbered";
  /** Backend role that may enter this workspace; null when the role does not exist yet. */
  backendRole: BackendRole | null;
  basePath: string;
  items: WorkspaceNavItem[];
  /** Secondary links at the bottom of icon-style sidebars. */
  footerItems?: WorkspaceNavItem[];
  /**
   * Presentation-only workspace served under /preview with fictional data (Phase 4G). It has no backend
   * role, is never a sign-in destination, and its routes are disabled in production by default.
   */
  preview?: boolean;
};

const supportFooter = (basePath: string): WorkspaceNavItem[] => [
  {
    id: "help",
    label: "Help & support",
    href: `${basePath}/help`,
    icon: ShieldCheck,
    backend: "none",
  },
  { id: "public", label: "Public website", href: "/", icon: ArrowRight, backend: "supported" },
];

function numbered(
  basePath: string,
  entries: Array<[id: string, label: string, backend: BackendSupport]>,
): WorkspaceNavItem[] {
  return entries.map(([id, label, backend]) => ({
    id,
    label,
    href: id === "overview" || id === "today" ? basePath : `${basePath}/${id}`,
    backend,
  }));
}

export const WORKSPACES: Record<WorkspaceId, WorkspaceDefinition> = {
  patient: {
    id: "patient",
    label: "Patient workspace",
    name: "Patient",
    navStyle: "icon",
    backendRole: "PATIENT",
    basePath: "/patient",
    items: [
      { id: "overview", label: "Overview", href: "/patient", icon: House, backend: "supported" },
      { id: "find-a-doctor", label: "Find a doctor", href: "/patient/find-a-doctor", icon: Heart, backend: "supported" },
      { id: "appointments", label: "Appointments", href: "/patient/appointments", icon: CalendarDays, backend: "supported" },
      { id: "messages", label: "Messages", href: "/patient/messages", icon: MessageSquareText, backend: "supported" },
      { id: "records", label: "Medical information", href: "/patient/records", icon: HeartPulse, backend: "supported" },
      { id: "payments", label: "Payments", href: "/patient/payments", icon: ReceiptText, backend: "none" },
    ],
    footerItems: supportFooter("/patient"),
  },
  doctor: {
    id: "doctor",
    label: "Doctor workspace",
    name: "Doctor",
    navStyle: "icon",
    backendRole: "PROVIDER",
    basePath: "/provider",
    items: [
      { id: "overview", label: "Overview", href: "/provider", icon: House, backend: "partial" },
      { id: "schedule", label: "Schedule", href: "/provider/schedule", icon: CalendarClock, backend: "supported" },
      { id: "appointments", label: "Appointments", href: "/provider/appointments", icon: CalendarDays, backend: "supported" },
      { id: "patients", label: "Patients", href: "/provider/patients", icon: Users, backend: "partial" },
      { id: "clinical-work", label: "Clinical work", href: "/provider/clinical-work", icon: Stethoscope, backend: "partial" },
      { id: "messages", label: "Messages", href: "/provider/messages", icon: MessageSquareText, backend: "supported" },
      { id: "earnings", label: "Earnings & payouts", href: "/provider/earnings", icon: WalletCards, backend: "none" },
    ],
    footerItems: supportFooter("/provider"),
  },
  laboratory: {
    id: "laboratory",
    label: "Laboratory workspace",
    name: "Laboratory",
    navStyle: "numbered",
    backendRole: "LABORATORY",
    basePath: "/laboratory",
    items: numbered("/laboratory", [
      ["overview", "Overview", "partial"],
      ["orders", "Orders", "partial"],
      ["specimens", "Specimens", "none"],
      ["results", "Results", "partial"],
      ["test-catalogue", "Test catalogue", "none"],
      ["pricing-payouts", "Pricing & payouts", "none"],
      ["audit-history", "Audit history", "none"],
    ]),
  },
  // Not in the approved reference (no home-care design); laid out like the laboratory workspace (Phase 5C).
  "home-care": {
    id: "home-care",
    label: "Home-care workspace",
    name: "Home care",
    navStyle: "numbered",
    backendRole: "HOME_CARE",
    basePath: "/home-care",
    items: numbered("/home-care", [
      ["overview", "Overview", "supported"],
      ["visits", "Visits", "supported"],
      ["help", "Help and guidance", "supported"],
    ]),
  },
  "collection-provider": {
    id: "collection-provider",
    label: "Collection provider workspace",
    name: "Collection provider",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/collection",
    items: numbered("/preview/collection", [
      ["overview", "Overview", "none"],
      ["pickup-requests", "Pickup requests", "none"],
      ["assignments", "Assignments", "none"],
      ["tracking", "Tracking", "none"],
      ["exceptions", "Exceptions", "none"],
      ["pricing-payouts", "Pricing & payouts", "none"],
      ["history", "History", "none"],
    ]),
  },
  "collection-agent": {
    id: "collection-agent",
    label: "Collection agent workspace",
    name: "Collection agent",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/collection-agent",
    items: numbered("/preview/collection-agent", [
      ["today", "Today", "none"],
      ["assigned-pickups", "Assigned pickups", "none"],
      ["offline-queue", "Offline queue", "none"],
      ["handoff-history", "Handoff history", "none"],
    ]),
  },
  "super-admin": {
    id: "super-admin",
    label: "Super administrator workspace",
    name: "Super administrator",
    navStyle: "numbered",
    backendRole: "SUPER_ADMIN",
    basePath: "/admin",
    items: numbered("/admin", [
      ["overview", "Overview", "supported"],
      ["users", "Users and roles", "supported"],
      ["providers", "Providers", "supported"],
      ["verification", "Verification", "partial"],
      ["security", "Security", "partial"],
      ["configuration", "Configuration", "partial"],
      // Not in the reference's super-administrator menu: the backend grants SUPER_ADMIN every
      // ADMIN capability, and these two keep the legacy console's queues reachable (Phase 4E).
      ["tasks", "Task queue", "supported"],
      ["appointments", "Appointments", "supported"],
    ]),
  },
  admin: {
    id: "admin",
    label: "Administrator workspace",
    name: "Administrator",
    navStyle: "numbered",
    backendRole: "ADMIN",
    basePath: "/admin",
    items: numbered("/admin", [
      ["overview", "Overview", "supported"],
      ["tasks", "Task queue", "supported"],
      ["onboarding", "Onboarding", "supported"],
      ["appointments", "Appointments", "supported"],
      ["escalations", "Escalations", "none"],
      ["reports", "Reports", "partial"],
    ]),
  },
  support: {
    id: "support",
    label: "Customer support workspace",
    name: "Customer support",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/support",
    items: numbered("/preview/support", [
      ["overview", "Overview", "none"],
      ["tickets", "Ticket queue", "none"],
      ["user-lookup", "User lookup", "none"],
      ["templates", "Templates", "none"],
      ["escalations", "Escalations", "none"],
      ["reports", "Reports", "none"],
    ]),
  },
  finance: {
    id: "finance",
    label: "Financial officer workspace",
    name: "Financial officer",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/finance",
    items: numbered("/preview/finance", [
      ["overview", "Overview", "none"],
      ["transactions", "Transactions", "none"],
      ["refunds", "Refunds", "none"],
      ["reconciliation", "Reconciliation", "none"],
      ["payouts", "Payouts", "none"],
      ["reports", "Reports", "none"],
    ]),
  },
  "specimen-lifecycle": {
    id: "specimen-lifecycle",
    label: "Specimen lifecycle",
    name: "Specimen lifecycle",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/specimen-lifecycle",
    items: numbered("/preview/specimen-lifecycle", [
      ["overview", "Workflow map", "none"],
      ["exception-paths", "Exception paths", "none"],
      ["dependencies", "Dependencies", "none"],
    ]),
  },
  "permissions-matrix": {
    id: "permissions-matrix",
    label: "Permissions matrix",
    name: "Permissions matrix",
    navStyle: "numbered",
    backendRole: null,
    preview: true,
    basePath: "/preview/permissions-matrix",
    items: numbered("/preview/permissions-matrix", [
      ["overview", "Permissions matrix", "none"],
      ["access-principles", "Access principles", "none"],
      ["audit-requirements", "Audit requirements", "none"],
    ]),
  },
};

export const WORKSPACE_IDS = Object.keys(WORKSPACES) as WorkspaceId[];

export function isWorkspaceId(value: string): value is WorkspaceId {
  return value in WORKSPACES;
}

/**
 * Where a signed-in user lands based on the role returned by the MedNet API.
 */
export function homePathForRole(role: string): string {
  switch (role) {
    case "ADMIN":
    case "SUPER_ADMIN":
      return "/admin";
    case "PATIENT":
      return "/patient";
    case "PROVIDER":
      return "/provider";
    case "LABORATORY":
      return "/laboratory";
    case "HOME_CARE":
      return "/home-care";
    default:
      return "/account";
  }
}

/**
 * A provider sign-up still awaiting approval: the account type is PROVIDER but the API grants only the
 * PATIENT role until an administrator approves the application. Routing only, never an access decision.
 */
export function isProviderApplicant(session: { role: string; accountType?: string | null }): boolean {
  return session.role === "PATIENT" && session.accountType === "PROVIDER";
}

/** Where a signed-in user lands: provider applicants go to their onboarding in the doctor workspace. */
export function homePathForSession(session: { role: string; accountType?: string | null }): string {
  return isProviderApplicant(session) ? "/provider" : homePathForRole(session.role);
}

/** Longest-prefix match so `/patient/records/1` activates "Medical information", not "Overview". */
export function activeNavItemId(
  workspace: WorkspaceDefinition,
  pathname: string,
): string | undefined {
  const candidates = [...workspace.items, ...(workspace.footerItems ?? [])]
    .filter((item) => item.href !== "/")
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length);
  return candidates[0]?.id;
}
