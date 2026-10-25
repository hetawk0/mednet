import { apiRequest } from "@/lib/api";
import type { AvailabilitySlot, ConsultationMode, Page } from "@/features/find-care/provider-api";
import type {
  Appointment,
  ClinicalRecord,
  Consultation,
  ConsultationMessage,
  Conversation,
  LabResult,
  Message,
  Notification,
  Vital,
} from "@/features/patient/patient-api";

/**
 * Provider-scoped API calls. Spring authorises every one (`hasRole('PROVIDER')` plus an APPROVED
 * provider record, the care relationship and, for clinical data, the patient's consent); the client
 * never decides access. Types are shared with the patient module where the backend record is shared.
 */

/** `ProviderService.ProviderApplication`. `credentialReference` is what the provider submitted, not a verification. */
export type ProviderApplication = {
  id: string;
  displayName: string;
  specialty: string;
  credentialReference: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED" | (string & {});
  createdAt: string;
  reviewedAt: string | null;
};

export type ProviderPatientSummary = {
  patientAccountId: string;
  name: string;
};

/** Actions a provider may send (`AppointmentController.AppointmentActionRequest`). */
export type ProviderAppointmentAction =
  | "ACCEPT"
  | "DECLINE"
  | "CANCEL"
  | "REQUEST_RESCHEDULE"
  | "ACCEPT_RESCHEDULE"
  | "DECLINE_RESCHEDULE"
  | "COMPLETE";

/** `PatientRecordController.CreateRecordRequest`. */
export const RECORD_CATEGORIES = ["ENCOUNTER_SUMMARY", "DIAGNOSIS", "PRESCRIPTION", "ALLERGY", "MEDICAL_HISTORY", "DOCUMENT"] as const;
export type RecordCategory = (typeof RECORD_CATEGORIES)[number];
export type NewRecord = {
  category: RecordCategory;
  title: string;
  clinicalCode?: string;
  summary: string;
  effectiveAt: string;
  amendsRecordId?: string;
};

export const PROVIDER_LIMITS = { recordTitle: 160, clinicalCode: 80, recordSummary: 10000, displayName: 160, specialty: 120, credentialReference: 120 } as const;

type Signal = AbortSignal | undefined;
const id = encodeURIComponent;

export const providerApi = {
  application: (signal?: Signal) => apiRequest<ProviderApplication>("/providers/me/application", { signal }),
  apply: (body: { displayName: string; specialty: string; credentialReference: string }) =>
    apiRequest<ProviderApplication>("/providers/applications", { method: "POST", body }),

  /** Open, future slots only (the public directory endpoint, earliest first). Reserved slots appear as appointments. */
  openSlots: (providerId: string, page = 0, size = 100, signal?: Signal) =>
    apiRequest<Page<AvailabilitySlot>>(`/providers/${id(providerId)}/availability`, { query: { page, size }, signal }),
  createSlot: (slot: { startsAt: string; endsAt: string; consultationMode: ConsultationMode }) =>
    apiRequest<AvailabilitySlot>("/providers/me/availability", { method: "POST", body: slot }),
  /** Closes an open slot; 409 once it is reserved by a request. */
  closeSlot: (slotId: string) => apiRequest<void>(`/providers/me/availability/${id(slotId)}`, { method: "DELETE" }),

  appointments: (page = 0, size = 100, signal?: Signal) =>
    apiRequest<Page<Appointment>>("/appointments", { query: { page, size }, signal }),
  patients: (page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<ProviderPatientSummary>>("/providers/me/patients", { query: { page, size }, signal }),
  patient: (patientAccountId: string, signal?: Signal) =>
    apiRequest<ProviderPatientSummary>(`/providers/me/patients/${id(patientAccountId)}`, { signal }),
  patientAppointments: (patientAccountId: string, page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<Appointment>>(`/providers/me/patients/${id(patientAccountId)}/appointments`, { query: { page, size }, signal }),
  appointment: (appointmentId: string, signal?: Signal) => apiRequest<Appointment>(`/appointments/${id(appointmentId)}`, { signal }),
  act: (appointmentId: string, action: ProviderAppointmentAction, proposedAvailabilitySlotId?: string) =>
    apiRequest<Appointment>(`/appointments/${id(appointmentId)}`, {
      method: "PATCH",
      body: { action, proposedAvailabilitySlotId },
    }),

  openConsultation: (appointmentId: string) =>
    apiRequest<Consultation>(`/appointments/${id(appointmentId)}/consultation`, { method: "POST" }),
  consultation: (consultationId: string, signal?: Signal) => apiRequest<Consultation>(`/consultations/${id(consultationId)}`, { signal }),
  consultationMessages: (consultationId: string, page = 0, size = 100, signal?: Signal) =>
    apiRequest<Page<ConsultationMessage>>(`/consultations/${id(consultationId)}/messages`, { query: { page, size }, signal }),
  sendConsultationMessage: (consultationId: string, body: string) =>
    apiRequest<ConsultationMessage>(`/consultations/${id(consultationId)}/messages`, { method: "POST", body: { body } }),
  endConsultation: (consultationId: string) =>
    apiRequest<Consultation>(`/consultations/${id(consultationId)}/end`, { method: "PATCH" }),

  /** Requires a care relationship and the patient's active consent (403 otherwise). */
  records: (patientAccountId: string, page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<ClinicalRecord>>(`/patients/${id(patientAccountId)}/records`, { query: { page, size }, signal }),
  addRecord: (patientAccountId: string, record: NewRecord) =>
    apiRequest<ClinicalRecord>(`/patients/${id(patientAccountId)}/records`, { method: "POST", body: record }),
  vitals: (patientAccountId: string, page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<Vital>>(`/patients/${id(patientAccountId)}/vitals`, { query: { page, size }, signal }),
  /**
   * `GET /providers/me/lab-review-queue` (Phase 5D): care patients with results awaiting review (record
   * access granted) or without record access, in one paged call with totals. No result text.
   */
  reviewQueue: (page = 0, size = 20, signal?: Signal) =>
    apiRequest<ReviewQueuePage>("/providers/me/lab-review-queue", { query: { page, size }, signal }),
  pendingLabResults: (patientAccountId: string, signal?: Signal) =>
    apiRequest<LabResult[]>(`/patients/${id(patientAccountId)}/lab-results/pending`, { signal }),
  pendingLabResultsPage: (patientAccountId: string, page: number, size: number, signal?: Signal) =>
    apiRequest<Page<LabResult>>(`/patients/${id(patientAccountId)}/lab-results/pending`, {
      query: { page, size },
      signal,
    }),
  reviewLabResult: (resultId: string, action: "RELEASE" | "RETURN") =>
    apiRequest<LabResult>(`/lab-results/${id(resultId)}/review`, { method: "PATCH", body: { action } }),

  /** Providers can reply in conversations patients open; only patients can start one. */
  conversations: (signal?: Signal) => apiRequest<Conversation[]>("/conversations", { signal }),
  messages: (conversationId: string, page = 0, size = 100, signal?: Signal) =>
    apiRequest<Page<Message>>(`/conversations/${id(conversationId)}/messages`, { query: { page, size }, signal }),
  sendMessage: (conversationId: string, body: string) =>
    apiRequest<Message>(`/conversations/${id(conversationId)}/messages`, { method: "POST", body: { body } }),

  notifications: (page = 0, size = 10, signal?: Signal) =>
    apiRequest<Page<Notification>>("/notifications", { query: { page, size }, signal }),
  unreadCount: (signal?: Signal) => apiRequest<number>("/notifications/unread-count", { signal }),
  markNotificationRead: (notificationId: string) =>
    apiRequest<Notification>(`/notifications/${id(notificationId)}/read`, { method: "PATCH" }),
};

export type ReviewQueueEntry = {
  patientAccountId: string;
  patientName: string | null;
  /** False: care relationship without record consent; no counts and no results can be opened. */
  recordAccess: boolean;
  pendingResults: number;
  oldestPendingAt: string | null;
};

export type ReviewQueuePage = Page<ReviewQueueEntry> & {
  /** Across every page. */
  totalPendingResults: number;
  patientsWithoutRecordAccess: number;
};
