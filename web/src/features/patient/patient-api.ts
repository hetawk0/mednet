import { apiRequest } from "@/lib/api";
import type { ConsultationMode, Page } from "@/features/find-care/provider-api";

/**
 * Patient-scoped API calls. Every endpoint here is `hasRole('PATIENT')` (or PATIENT/PROVIDER) in
 * Spring and resolves the patient from the session, so the client never sends a patient id.
 * Types mirror the backend records named on each type; only fields the UI reads are relied on.
 */

/** `AppointmentService.AppointmentDetails`. There is no provider id or reschedule initiator in the payload. */
export type Appointment = {
  id: string;
  patientAccountId: string;
  status: string;
  /** The patient's profile name (shown to the provider; the patient sees their own). */
  patientName: string;
  providerName: string;
  consultationMode: ConsultationMode;
  startsAt: string;
  endsAt: string;
  proposedStartsAt: string | null;
  proposedEndsAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Added in Phase 4D (additive). Optional so older responses still type-check. */
  providerId?: string;
  providerSpecialty?: string;
  /** PATIENT or PROVIDER while a reschedule is pending, else null. */
  rescheduleRequestedBy?: "PATIENT" | "PROVIDER" | null;
};

/** Actions a patient may send (`AppointmentController.AppointmentActionRequest`); provider-only actions are excluded. */
export type PatientAppointmentAction = "CANCEL" | "ACCEPT_RESCHEDULE" | "DECLINE_RESCHEDULE";

/** `PatientServiceRequestService.PatientServiceRequestDetails`. */
export type ServiceRequest = {
  id: string;
  requestType: string;
  requestedService: string;
  locationDescription: string | null;
  status: string;
  scheduledAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/** `NotificationService.NotificationDetails` — titles are generic by design; no clinical content. */
export type Notification = {
  id: string;
  eventType: string;
  title: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
  readAt: string | null;
};

/** `PatientProfileService.PatientProfile`. */
export type PatientProfile = {
  fullName: string;
  dateOfBirth: string;
  phoneNumber: string;
  address: string;
};

/** `PatientRecordService.RecordDetails`. */
export type ClinicalRecord = {
  id: string;
  category: string;
  title: string;
  clinicalCode: string | null;
  summary: string;
  effectiveAt: string;
  createdAt: string;
  amendsRecordId: string | null;
  authorName: string;
};

/** `PatientRecordService.RecordConsent`. */
export type RecordConsent = {
  providerId: string;
  providerName: string;
  specialty: string;
  status: "GRANTED" | "REVOKED" | (string & {});
  grantedAt: string | null;
  revokedAt: string | null;
};

/** `LaboratoryResultService.LaboratoryResultDetails` — patients only receive RELEASED results. */
export type LabResult = {
  id: string;
  requestId: string;
  summary: string;
  status: string;
  createdAt: string;
  reviewedAt: string | null;
};

/** `MedicationService.MedicationDetails`. */
export type Medication = {
  id: string;
  medicationName: string;
  dose: string;
  reminderTime: string;
  timeZone: string;
  startDate: string;
  endDate: string | null;
  nextReminderAt: string | null;
  status: string;
};

export type DoseLog = {
  id: string;
  doseDate: string;
  takenAt: string;
};

/** `MedicationController.MedicationRequest` — times are `HH:mm`, dates `yyyy-MM-dd`, the zone an IANA id. */
export type MedicationInput = {
  medicationName: string;
  dose: string;
  reminderTime: string;
  timeZone: string;
  startDate: string;
  endDate: string | null;
};

/** `PatientVitalService.PatientVitalDetails`. */
export type Vital = {
  id: string;
  metric: string;
  value: number;
  unit: string;
  recordedAt: string;
  source: string;
};

/** `ConsultationService.ConsultationDetails` / `ConsultationMessageDetails` (text consultations only). */
export type Consultation = {
  id: string;
  appointmentId: string;
  status: "OPEN" | "ENDED" | (string & {});
  openedAt: string;
  endedAt: string | null;
};

export type ConsultationMessage = { id: string; senderAccountId: string; body: string; createdAt: string };

/** `MessageService.ConversationDetails` / `MessageDetails`. */
export type Conversation = {
  id: string;
  patientAccountId: string;
  providerId: string;
  providerDisplayName: string;
  createdAt: string;
  updatedAt: string;
};

export type Message = {
  id: string;
  conversationId: string;
  senderAccountId: string;
  body: string;
  createdAt: string;
};

/** Backend validation limits (`@Size` on the request records). */
export const LIMITS = {
  fullName: 160,
  phoneNumber: 32,
  address: 500,
  serviceDescription: 240,
  locationDescription: 500,
  testDescription: 240,
  medicationName: 160,
  dose: 120,
  vitalMetric: 80,
  vitalUnit: 40,
  messageBody: 4000,
} as const;

type Signal = AbortSignal | undefined;

export const patientApi = {
  appointments: (page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<Appointment>>("/appointments", { query: { page, size }, signal }),
  appointment: (id: string, signal?: Signal) => apiRequest<Appointment>(`/appointments/${encodeURIComponent(id)}`, { signal }),
  actOnAppointment: (id: string, action: PatientAppointmentAction) =>
    apiRequest<Appointment>(`/appointments/${encodeURIComponent(id)}`, { method: "PATCH", body: { action } }),

  homeCareRequests: (signal?: Signal) => apiRequest<ServiceRequest[]>("/home-care-requests", { signal }),
  homeCareRequestsPage: (page: number, size: number, filters: { status?: string; search?: string }, signal?: Signal) =>
    apiRequest<Page<ServiceRequest>>("/home-care-requests", {
      query: { page, size, status: filters.status || undefined, search: filters.search?.trim() || undefined },
      signal,
    }),
  requestHomeCare: (serviceDescription: string, locationDescription: string) =>
    apiRequest<ServiceRequest>("/home-care-requests", { method: "POST", body: { serviceDescription, locationDescription } }),
  cancelHomeCare: (id: string) =>
    apiRequest<ServiceRequest>(`/home-care-requests/${encodeURIComponent(id)}/cancel`, { method: "PATCH" }),
  labRequests: (signal?: Signal) => apiRequest<ServiceRequest[]>("/lab-requests", { signal }),
  labRequestsPage: (page: number, size: number, filters: { status?: string; search?: string }, signal?: Signal) =>
    apiRequest<Page<ServiceRequest>>("/lab-requests", {
      query: { page, size, status: filters.status || undefined, search: filters.search?.trim() || undefined },
      signal,
    }),
  requestLab: (testDescription: string) =>
    apiRequest<ServiceRequest>("/lab-requests", { method: "POST", body: { testDescription } }),
  cancelLab: (id: string) => apiRequest<ServiceRequest>(`/lab-requests/${encodeURIComponent(id)}/cancel`, { method: "PATCH" }),

  notifications: (page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<Notification>>("/notifications", { query: { page, size }, signal }),
  unreadNotifications: (signal?: Signal) => apiRequest<number>("/notifications/unread-count", { signal }),
  markNotificationRead: (id: string) =>
    apiRequest<Notification>(`/notifications/${encodeURIComponent(id)}/read`, { method: "PATCH" }),

  profile: (signal?: Signal) => apiRequest<PatientProfile>("/patients/me/profile", { signal }),
  saveProfile: (profile: PatientProfile) =>
    apiRequest<PatientProfile>("/patients/me/profile", { method: "PUT", body: profile }),

  records: (page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<ClinicalRecord>>("/patients/me/records", { query: { page, size }, signal }),
  consents: (signal?: Signal) => apiRequest<RecordConsent[]>("/patients/me/record-consents", { signal }),
  grantConsent: (providerId: string) =>
    apiRequest<RecordConsent>("/patients/me/record-consents", { method: "POST", body: { providerId } }),
  revokeConsent: (providerId: string) =>
    apiRequest<RecordConsent>(`/patients/me/record-consents/${encodeURIComponent(providerId)}`, { method: "DELETE" }),

  /** Released results only, newest first, paged and complete (Phase 5D; the unpaged list stops at 100). */
  labResults: (page = 0, size = 20, signal?: Signal) =>
    apiRequest<Page<LabResult>>("/patients/me/lab-results", { query: { page, size }, signal }),
  medications: (signal?: Signal) => apiRequest<Medication[]>("/patients/me/medications", { signal }),
  medicationsPage: (page: number, size: number, signal?: Signal) =>
    apiRequest<Page<Medication>>("/patients/me/medications", { query: { page, size }, signal }),
  doseLogs: (id: string, page: number, size: number, signal?: Signal) =>
    apiRequest<Page<DoseLog>>(`/patients/me/medications/${encodeURIComponent(id)}/dose-logs`, {
      query: { page, size },
      signal,
    }),
  createMedication: (medication: MedicationInput) =>
    apiRequest<Medication>("/patients/me/medications", { method: "POST", body: medication }),
  stopMedication: (id: string) =>
    apiRequest<Medication>(`/patients/me/medications/${encodeURIComponent(id)}/stop`, { method: "PATCH" }),
  /** Records today's dose (the API decides "today" in the schedule's time zone). */
  logDose: (id: string) =>
    apiRequest<{ id: string; doseDate: string; takenAt: string }>(`/patients/me/medications/${encodeURIComponent(id)}/dose-logs`, {
      method: "POST",
    }),
  vitals: (page = 0, size = 25, signal?: Signal) =>
    apiRequest<Page<Vital>>("/patients/me/vitals", { query: { page, size }, signal }),
  recordVital: (vital: { metric: string; value: number; unit: string; recordedAt: string }) =>
    apiRequest<Vital>("/patients/me/vitals", { method: "POST", body: vital }),

  /**
   * Opens (or returns the already open) text consultation for a CONFIRMED TEXT appointment. Opening
   * notifies the doctor, so it is only called from an explicit button. 409 when not possible.
   */
  openConsultation: (appointmentId: string) =>
    apiRequest<Consultation>(`/appointments/${encodeURIComponent(appointmentId)}/consultation`, { method: "POST" }),
  consultation: (id: string, signal?: Signal) => apiRequest<Consultation>(`/consultations/${encodeURIComponent(id)}`, { signal }),
  consultationMessages: (id: string, page = 0, size = 100, signal?: Signal) =>
    apiRequest<Page<ConsultationMessage>>(`/consultations/${encodeURIComponent(id)}/messages`, { query: { page, size }, signal }),
  sendConsultationMessage: (id: string, body: string) =>
    apiRequest<ConsultationMessage>(`/consultations/${encodeURIComponent(id)}/messages`, { method: "POST", body: { body } }),

  conversations: (signal?: Signal) => apiRequest<Conversation[]>("/conversations", { signal }),
  openConversation: (providerId: string) =>
    apiRequest<Conversation>("/conversations", { method: "POST", body: { providerId } }),
  messages: (conversationId: string, page = 0, size = 50, signal?: Signal) =>
    apiRequest<Page<Message>>(`/conversations/${encodeURIComponent(conversationId)}/messages`, {
      query: { page, size },
      signal,
    }),
  sendMessage: (conversationId: string, body: string) =>
    apiRequest<Message>(`/conversations/${encodeURIComponent(conversationId)}/messages`, {
      method: "POST",
      body: { body },
    }),
};
