import { apiRequest } from "@/lib/api";
import type { Page } from "@/features/find-care/provider-api";

/**
 * Administration API (`/api/v1/admin/**`). Spring Security admits only ADMIN and SUPER_ADMIN to
 * these paths and adds `hasRole('SUPER_ADMIN')` to account edit, role, verification and delete.
 * The client mirrors those rules to choose which controls to show; it never decides access.
 */

/** `AdminOverviewController.AdminOverview`. `counts` is null when PostgreSQL is not configured. */
export type AdminOverview = {
  apiStatus: string;
  checkedAt: string;
  uptimeSeconds: number;
  counts: {
    pendingProviders: number;
    activeAccounts: number;
    openRequests: number;
    openHomeCareRequests: number;
    openLaboratoryRequests: number;
  } | null;
  modules: Array<{ key: string; status: "CONNECTED" | "NOT_CONFIGURED" | "DISABLED" | (string & {}) }>;
};

/** `AdminWorkflowModels.ProviderApplication`. Approval is an administrative decision, not a credential check. */
export type AdminProviderApplication = {
  id: string;
  displayName: string;
  email: string;
  specialty: string;
  credentialReference: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED" | (string & {});
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
};

/** `AdminWorkflowModels.PlatformAccount`. `id` is the internal key used in API paths; people see `publicId`. */
export type PlatformAccount = {
  id: string;
  publicId: string;
  displayName: string | null;
  email: string;
  accountType: AccountType | (string & {});
  status: "ACTIVE" | "SUSPENDED" | (string & {});
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
};

/** `AdminWorkflowService.ACCOUNT_TYPES`; only CREATABLE_ACCOUNT_TYPES can be created. */
export const ACCOUNT_TYPES = ["PATIENT", "PROVIDER", "LABORATORY", "HOME_CARE", "ADMIN", "SUPER_ADMIN"] as const;
export type AccountType = (typeof ACCOUNT_TYPES)[number];
export const CREATABLE_ACCOUNT_TYPES = ["PATIENT", "PROVIDER", "LABORATORY", "HOME_CARE"] as const satisfies readonly AccountType[];

/** Admin-entered operational reference (`AdminWorkflowModels.ServiceRequest`). */
export type OperationalRequest = {
  id: string;
  referenceId: string;
  requestType: "APPOINTMENT" | "HOME_CARE" | "LABORATORY" | (string & {});
  requesterEmail: string;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED" | (string & {});
  createdAt: string;
  updatedAt: string;
};
export const OPERATIONAL_REQUEST_TYPES = ["APPOINTMENT", "HOME_CARE", "LABORATORY"] as const;

/** Patient home-care or laboratory request as administrators see it (`AdminRequestSummary`): no clinical detail. */
export type PatientServiceRequestSummary = {
  id: string;
  requestType: "HOME_CARE" | "LABORATORY" | (string & {});
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED" | (string & {});
  createdAt: string;
  /** Public account ID of the assigned partner staff member, or null/absent when unassigned (Phase 4F). */
  assignedStaffPublicId?: string | null;
};

/** `AppointmentService.AdminAppointmentSummary`: identifiers and timing only, no names or reasons. */
export type AdminAppointment = {
  id: string;
  patientAccountId: string;
  providerApplicationId: string;
  status: string;
  consultationMode: string;
  startsAt: string;
  endsAt: string;
  createdAt: string;
  updatedAt: string;
};

/** `AdminWorkflowModels.AdminAuditEvent` (latest 100). */
export type AuditEvent = {
  id: string;
  actorEmail: string;
  action: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
};

export type AggregatePoint = {
  month: string;
  category: string;
  status: string;
  count: number;
};

export type SnapshotPoint = {
  category: string;
  status: string;
  count: number;
};

export type AdminAggregateReport = {
  from: string;
  through: string;
  generatedAt: string;
  accounts: AggregatePoint[];
  totalRegisteredPatients: number;
  patientRegistrations: AggregatePoint[];
  providersByStatus: SnapshotPoint[];
  appointments: AggregatePoint[];
  completedVirtualConsultations: AggregatePoint[];
  serviceRequests: AggregatePoint[];
  laboratoryResults: AggregatePoint[];
  awaitingAction: SnapshotPoint[];
};

export type AccountFilters = {
  search?: string;
  status?: string;
  accountType?: string;
  emailVerified?: "true" | "false" | "";
};

/** Request limits from the controller's Bean Validation annotations. */
export const ADMIN_LIMITS = {
  displayName: 160,
  email: 254,
  specialty: 120,
  credentialReference: 120,
  referenceId: 80,
  passwordMin: 12,
  passwordMax: 72,
} as const;

type Signal = AbortSignal | undefined;
const id = encodeURIComponent;

export const adminApi = {
  overview: (signal?: Signal) => apiRequest<AdminOverview>("/admin/overview", { signal }),
  aggregateReport: (filters: { from?: string; through?: string }, signal?: Signal) =>
    apiRequest<AdminAggregateReport>("/admin/reports", {
      query: { from: filters.from || undefined, through: filters.through || undefined },
      signal,
    }),

  /** Paged and complete (Phase 5D); `search` matches name, email, specialty or credential reference. */
  providersPage: (filters: { status?: string; search?: string }, page: number, size: number, signal?: Signal) =>
    apiRequest<Page<AdminProviderApplication>>("/admin/providers", {
      query: { page, size, status: filters.status || undefined, search: filters.search?.trim() || undefined },
      signal,
    }),
  createProvider: (body: { displayName: string; email: string; specialty: string; credentialReference: string }) =>
    apiRequest<AdminProviderApplication>("/admin/providers", { method: "POST", body }),
  reviewProvider: (applicationId: string, status: "APPROVED" | "REJECTED" | "SUSPENDED") =>
    apiRequest<AdminProviderApplication>(`/admin/providers/${id(applicationId)}/status`, { method: "PATCH", body: { status } }),

  accounts: (page: number, size: number, filters: AccountFilters, signal?: Signal) =>
    apiRequest<Page<PlatformAccount>>("/admin/accounts", {
      query: {
        page,
        size,
        search: filters.search?.trim() || undefined,
        status: filters.status || undefined,
        accountType: filters.accountType || undefined,
        emailVerified: filters.emailVerified || undefined,
      },
      signal,
    }),
  createAccount: (body: { displayName: string; email: string; accountType: string; password: string }) =>
    apiRequest<PlatformAccount>("/admin/accounts", { method: "POST", body }),
  /** SUPER_ADMIN only. Omit `password` to keep the current one. */
  updateAccount: (accountId: string, body: { displayName: string; email: string; accountType: string; password?: string }) =>
    apiRequest<PlatformAccount>(`/admin/accounts/${id(accountId)}`, { method: "PUT", body }),
  changeAccountStatus: (accountId: string, status: "ACTIVE" | "SUSPENDED") =>
    apiRequest<PlatformAccount>(`/admin/accounts/${id(accountId)}/status`, { method: "PATCH", body: { status } }),
  /** SUPER_ADMIN only. */
  changeAccountVerification: (accountId: string, emailVerified: boolean) =>
    apiRequest<PlatformAccount>(`/admin/accounts/${id(accountId)}/verification`, { method: "PATCH", body: { emailVerified } }),
  /** SUPER_ADMIN only. */
  deleteAccount: (accountId: string) => apiRequest<void>(`/admin/accounts/${id(accountId)}`, { method: "DELETE" }),

  requestsPage: (page: number, size: number, signal?: Signal) =>
    apiRequest<Page<OperationalRequest>>("/admin/requests", { query: { page, size }, signal }),
  createRequest: (body: { referenceId: string; requestType: string; requesterEmail: string }) =>
    apiRequest<OperationalRequest>("/admin/requests", { method: "POST", body }),
  changeRequestStatus: (requestId: string, status: "IN_PROGRESS" | "RESOLVED") =>
    apiRequest<OperationalRequest>(`/admin/requests/${id(requestId)}/status`, { method: "PATCH", body: { status } }),

  /** Paged and complete (Phase 5D); `statuses` empty means every status. */
  serviceRequestsPage: (type: "HOME_CARE" | "LABORATORY", statuses: string[], page: number, size: number, signal?: Signal) =>
    apiRequest<Page<PatientServiceRequestSummary>>("/admin/service-requests", { query: { type, status: statuses, page, size }, signal }),
  changeServiceRequestStatus: (requestId: string, status: "IN_PROGRESS" | "CANCELLED") =>
    apiRequest<PatientServiceRequestSummary>(`/admin/service-requests/${id(requestId)}/status`, { method: "PATCH", body: { status } }),
  assignServiceRequest: (requestId: string, staffAccountId: string) =>
    apiRequest<{ requestId: string; assignedStaffAccountId: string; requestType: string; status: string }>(
      `/admin/service-requests/${id(requestId)}/assignment`,
      { method: "PATCH", body: { staffAccountId } },
    ),

  appointments: (page: number, size: number, signal?: Signal) =>
    apiRequest<Page<AdminAppointment>>("/admin/appointments", { query: { page, size }, signal }),

  /**
   * Paged and filtered on the server (Phase 5D). `labelActions`: action codes whose readable label
   * matches `search`, so searching by the wording shown in the table still finds them.
   */
  auditPage: (page: number, size: number, filters: { search?: string; resourceType?: string; labelActions?: string[] }, signal?: Signal) =>
    apiRequest<Page<AuditEvent>>("/admin/audit", {
      query: {
        page,
        size,
        search: filters.search?.trim() || undefined,
        resourceType: filters.resourceType || undefined,
        labelAction: filters.search?.trim() ? filters.labelActions : undefined,
      },
      signal,
    }),
  auditResourceTypes: (signal?: Signal) => apiRequest<string[]>("/admin/audit/resource-types", { signal }),
};

/** 24 random bytes as URL-safe base64 (32 characters), within the 12–72 character password rule. */
export function generatePassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}
