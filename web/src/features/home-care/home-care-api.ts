import { apiRequest } from "@/lib/api";
import type { Page } from "@/features/find-care/provider-api";

/**
 * Home-care partner API (Phase 5C). Spring admits only an active, email-verified HOME_CARE account and
 * then only to requests an administrator assigned to it (`PatientServiceRequestService.requirePartnerAccount`);
 * anything else answers 403 or 404.
 */

/** `PatientServiceRequestService.PartnerRequestDetails`: no patient name, contact or clinical record. */
export type HomeCareRequest = {
  id: string;
  requestType: "HOME_CARE" | (string & {});
  /** The care the patient asked for, in their words. */
  requestedService: string;
  /** Where the patient asked to be visited, in their words. */
  locationDescription: string | null;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED" | (string & {});
  /** The visit time the assigned staff member set; null until scheduled. */
  scheduledAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PartnerRequestFilters = {
  page: number;
  size: number;
  status?: string;
  search?: string;
};

const id = encodeURIComponent;

export const homeCareApi = {
  requests: (signal?: AbortSignal) =>
    apiRequest<HomeCareRequest[]>("/partner/service-requests", { query: { type: "HOME_CARE" }, signal }),
  requestsPage: ({ page, size, status, search }: PartnerRequestFilters, signal?: AbortSignal) =>
    apiRequest<Page<HomeCareRequest>>("/partner/service-requests", {
      query: { type: "HOME_CARE", page, size, status: status || undefined, search: search?.trim() || undefined },
      signal,
    }),
  /** Sets the visit time once, while the request is OPEN and unscheduled; it must be in the future. */
  schedule: (requestId: string, scheduledAt: string) =>
    apiRequest<HomeCareRequest>(`/partner/service-requests/${id(requestId)}/schedule`, { method: "PATCH", body: { scheduledAt } }),
  /** OPEN (scheduled) → IN_PROGRESS. */
  startVisit: (requestId: string) =>
    apiRequest<HomeCareRequest>(`/partner/service-requests/${id(requestId)}/status`, { method: "PATCH", body: { status: "IN_PROGRESS" } }),
  /** IN_PROGRESS → RESOLVED. */
  completeVisit: (requestId: string) =>
    apiRequest<HomeCareRequest>(`/partner/service-requests/${id(requestId)}/status`, { method: "PATCH", body: { status: "RESOLVED" } }),
};
