import { apiRequest } from "@/lib/api";
import type { Page } from "@/features/find-care/provider-api";

/**
 * Laboratory partner API. Spring admits only an active, email-verified LABORATORY account and then
 * only to requests assigned to that account (`PatientServiceRequestService.requirePartnerAccount`,
 * `LaboratoryResultService.requireLaboratoryStaff`); anything else answers 403 or 404.
 */

/** `PatientServiceRequestService.PartnerRequestDetails`: no patient identity, no clinical record. */
export type LabRequest = {
  id: string;
  requestType: "LABORATORY" | (string & {});
  /** The test the patient asked for, in their words. */
  requestedService: string;
  locationDescription: string | null;
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED" | (string & {});
  scheduledAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Newest submitted result's review state, listed with the order (Phase 5D); null before any result. */
  latestResult?: LatestResult | null;
};

/** `PatientServiceRequestService.LatestResult`: review state only, never the result text. */
export type LatestResult = Pick<SubmittedResult, "id" | "status" | "createdAt" | "reviewedAt">;

/**
 * `LaboratoryResultService.LaboratoryResultDetails`: returned after submission and by
 * `GET /partner/lab-results` (the laboratory's own submissions on requests still assigned to it).
 */
export type SubmittedResult = {
  id: string;
  requestId: string;
  summary: string;
  status: "PENDING_REVIEW" | "RELEASED" | "RETURNED" | (string & {});
  createdAt: string;
  reviewedAt: string | null;
};

/** `LaboratoryResultController.SubmitResultRequest`. */
export const LAB_LIMITS = { resultSummary: 9700 } as const;

const id = encodeURIComponent;

export const labApi = {
  requests: (signal?: AbortSignal) => apiRequest<LabRequest[]>("/partner/service-requests", { query: { type: "LABORATORY" }, signal }),
  requestsPage: (
    { status, search, page, size }: { status?: string; search?: string; page: number; size: number },
    signal?: AbortSignal,
  ) =>
    apiRequest<Page<LabRequest>>("/partner/service-requests", {
      query: { type: "LABORATORY", status: status || undefined, search: search?.trim() || undefined, page, size },
      signal,
    }),
  /** OPEN → IN_PROGRESS is the only status change a laboratory may make. */
  startProcessing: (requestId: string) =>
    apiRequest<LabRequest>(`/partner/service-requests/${id(requestId)}/status`, { method: "PATCH", body: { status: "IN_PROGRESS" } }),
  /** Own submissions, newest first, one review status per call, paged and complete (Phase 5D). */
  resultsPage: (status: SubmittedResult["status"], page: number, size: number, signal?: AbortSignal) =>
    apiRequest<Page<SubmittedResult>>("/partner/lab-results", { query: { status, page, size }, signal }),
  /** Creates a result in PENDING_REVIEW. A doctor with the patient's consent releases or returns it. */
  submitResult: (requestId: string, summary: string) =>
    apiRequest<SubmittedResult>(`/partner/service-requests/${id(requestId)}/lab-result`, { method: "POST", body: { summary } }),
};
