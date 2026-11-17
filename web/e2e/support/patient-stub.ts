import type { Page, Request } from "@playwright/test";
import { filterQueue, pageOf, preferencesStub } from "./paging";

/**
 * In-browser stand-in for the patient APIs, shaped like the Spring records in
 * features/patient/patient-api.ts. Real integration is covered by patient.live.spec.ts.
 */
const PATIENT_ID = "pat-1";
const hour = 3_600_000;
const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * hour).toISOString();

export type PatientStubOptions = {
  role?: string;
  signedOut?: boolean;
  /** 404 from /patients/me/profile (profile not completed). */
  noProfile?: boolean;
  empty?: boolean;
  apiDown?: boolean;
  /** Paths (exact, without /api/v1) answered with 403. */
  forbidden?: string[];
  /** The patient has applied to provide care (GET /providers/me/application answers 200). */
  providerApplication?: "PENDING" | "REJECTED";
};

export type PatientStubControls = {
  /** From now on every API call answers 401, as when the session expires mid-use. */
  expireSession: () => void;
};

export type StubLog = { requests: Array<{ method: string; path: string; body: unknown; csrf: string | null }> };

const page0 = <T,>(content: T[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 25 });

export function fixtures() {
  const appointments = [
    {
      id: "apt-next",
      patientAccountId: PATIENT_ID,
      status: "CONFIRMED",
      patientName: "James Brown",
      providerName: "Dr. Mariama Cole",
      providerId: "prov-cole",
      providerSpecialty: "General medicine",
      rescheduleRequestedBy: null as string | null,
      consultationMode: "TEXT",
      startsAt: iso(26),
      endsAt: iso(26.5),
      proposedStartsAt: null,
      proposedEndsAt: null,
      createdAt: iso(-48),
      updatedAt: iso(-24),
    },
    {
      id: "apt-moved",
      patientAccountId: PATIENT_ID,
      status: "RESCHEDULE_REQUESTED",
      patientName: "James Brown",
      providerName: "Dr. Samuel Kollie",
      providerId: "prov-kollie",
      providerSpecialty: "Pediatrics",
      rescheduleRequestedBy: "PROVIDER" as string | null,
      consultationMode: "IN_PERSON",
      startsAt: iso(72),
      endsAt: iso(72.5),
      proposedStartsAt: iso(96),
      proposedEndsAt: iso(96.5),
      createdAt: iso(-30),
      updatedAt: iso(-2),
    },
    {
      id: "apt-past",
      patientAccountId: PATIENT_ID,
      status: "COMPLETED",
      patientName: "James Brown",
      providerName: "Dr. Mariama Cole",
      providerId: "prov-cole",
      providerSpecialty: "General medicine",
      rescheduleRequestedBy: null as string | null,
      consultationMode: "TEXT",
      startsAt: iso(-100),
      endsAt: iso(-99.5),
      proposedStartsAt: null,
      proposedEndsAt: null,
      createdAt: iso(-200),
      updatedAt: iso(-99),
    },
  ];
  return {
    appointments,
    profile: { fullName: "James Brown", dateOfBirth: "1990-04-12", phoneNumber: "+231 770 000 000", address: "Sinkor, Monrovia" },
    records: [
      {
        id: "rec-1",
        category: "ENCOUNTER_SUMMARY",
        title: "Follow-up consultation",
        clinicalCode: null,
        summary: "Discussed progress. Continue current plan.",
        effectiveAt: iso(-99),
        createdAt: iso(-99),
        amendsRecordId: null,
        authorName: "Dr. Mariama Cole",
      },
    ],
    labResults: [{ id: "lab-1", requestId: "req-lab", summary: "Complete blood count within the reported reference ranges.", status: "RELEASED", createdAt: iso(-60), reviewedAt: iso(-50) }],
    labRequests: [
      { id: "req-lab", requestType: "LABORATORY", requestedService: "Complete blood count", locationDescription: null, status: "RESOLVED", scheduledAt: null, createdAt: iso(-120), updatedAt: iso(-60) },
    ],
    homeCare: [
      { id: "req-home", requestType: "HOME_CARE", requestedService: "Wound dressing", locationDescription: "Sinkor, near the market", status: "OPEN", scheduledAt: null, createdAt: iso(-5), updatedAt: iso(-5) },
    ],
    notifications: [
      { id: "n-1", eventType: "APPOINTMENT", title: "Your appointment status was updated", resourceType: "appointment", resourceId: "apt-moved", createdAt: iso(-2), readAt: null },
    ],
    providers: [
      { id: "prov-cole", displayName: "Dr. Mariama Cole", specialty: "General medicine" },
      { id: "prov-kollie", displayName: "Dr. Samuel Kollie", specialty: "Pediatrics" },
    ],
    consents: [] as Array<Record<string, unknown>>,
    conversations: [
      { id: "conv-1", patientAccountId: PATIENT_ID, providerId: "prov-cole", providerDisplayName: "Dr. Mariama Cole", createdAt: iso(-90), updatedAt: iso(-3) },
    ],
    messages: [
      { id: "m-1", conversationId: "conv-1", senderAccountId: "acc-cole", body: "How are you feeling after the visit?", createdAt: iso(-3) },
    ],
    medications: [
      {
        id: "med-1",
        medicationName: "Amoxicillin",
        dose: "1 capsule",
        reminderTime: "08:00:00",
        timeZone: "Africa/Monrovia",
        startDate: new Date(Date.now() - 2 * 24 * hour).toISOString().slice(0, 10),
        endDate: null,
        nextReminderAt: iso(12),
        status: "ACTIVE",
      },
    ],
    vitals: [{ id: "v-1", metric: "Weight", value: 72.5, unit: "kg", recordedAt: iso(-20), source: "PATIENT_REPORTED" }],
  };
}

const json = (body: unknown, status = 200) => ({ status, contentType: "application/json", body: JSON.stringify(body) });
const problem = (status: number, message: string) => json({ success: false, error: { code: `HTTP_${status}`, message, details: [] } }, status);

export async function stubPatientApi(
  page: Page,
  options: PatientStubOptions = {},
): Promise<StubLog & PatientStubControls & { data: ReturnType<typeof fixtures> }> {
  let expired = false;
  const consultations: Record<string, { id: string; appointmentId: string; status: string; openedAt: string; endedAt: null }> = {};
  const consultationMessages: Array<{ id: string; senderAccountId: string; body: string; createdAt: string }> = [];
  const data = fixtures();
  if (options.empty) {
    for (const key of ["appointments", "records", "labResults", "labRequests", "homeCare", "notifications", "conversations", "messages", "medications", "vitals"] as const) {
      (data[key] as unknown[]).length = 0;
    }
  }
  const log: StubLog = { requests: [] };
  const preferences = preferencesStub();

  options = { ...options };
  await page.route("**/api/v1/**", async (route, request: Request) => {
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData()!) : undefined;
    if (method !== "GET") log.requests.push({ method, path, body, csrf: request.headers()["x-xsrf-token"] ?? null });

    if (path === "/auth/csrf") return route.fulfill(json({ headerName: "X-XSRF-TOKEN", token: "test-token" }));
    if (path === "/auth/google/status") return route.fulfill(json({ enabled: false }));
    if (expired) return route.fulfill(problem(401, "Sign-in required"));
    if (options.forbidden?.includes(path)) return route.fulfill(problem(403, "Access is not allowed"));
    if (path === "/auth/session") {
      if (options.apiDown) return route.fulfill(problem(503, "Service unavailable"));
      if (options.signedOut) return route.fulfill(problem(401, "Sign-in required"));
      return route.fulfill(json({ email: "james@mednet.test", role: options.role ?? "PATIENT" }));
    }
    if (path === "/providers/me/application" && options.providerApplication) {
      return route.fulfill(
        json({
          id: "app-ama",
          displayName: "Dr. Ama Kollie",
          specialty: "Family medicine",
          credentialReference: "LMDC-3001",
          status: options.providerApplication,
          createdAt: "2026-10-01T09:00:00Z",
          reviewedAt: null,
        }),
      );
    }
    if (path === "/auth/logout") {
      options.signedOut = true;
      return route.fulfill({ status: 204 });
    }
    if (options.apiDown) return route.fulfill(problem(503, "Service unavailable"));

    if (path === "/patients/me/profile") {
      if (method === "PUT") {
        data.profile = body;
        options.noProfile = false;
        return route.fulfill(json({ ...body, createdAt: iso(0), updatedAt: iso(0) }));
      }
      return options.noProfile ? route.fulfill(problem(404, "Patient profile not found")) : route.fulfill(json(data.profile));
    }
    if (path === "/appointments" && method === "GET") return route.fulfill(json(page0(data.appointments)));
    const appointmentMatch = path.match(/^\/appointments\/([^/]+)$/);
    if (appointmentMatch && method === "GET") {
      const appointment = data.appointments.find((item) => item.id === appointmentMatch[1]);
      return route.fulfill(appointment ? json(appointment) : problem(404, "Appointment not found"));
    }
    const openMatch = path.match(/^\/appointments\/([^/]+)\/consultation$/);
    if (openMatch && method === "POST") {
      const appointment = data.appointments.find((item) => item.id === openMatch[1])!;
      if (appointment.status !== "CONFIRMED") return route.fulfill(problem(409, "A confirmed appointment is required"));
      consultations[appointment.id] ??= { id: `con-${appointment.id}`, appointmentId: appointment.id, status: "OPEN", openedAt: iso(0), endedAt: null };
      return route.fulfill(json(consultations[appointment.id]));
    }
    const consultationMatch = path.match(/^\/consultations\/([^/]+)(\/messages)?$/);
    if (consultationMatch) {
      const consultation = Object.values(consultations).find((item) => item.id === consultationMatch[1]);
      if (!consultation) return route.fulfill(problem(404, "Consultation not found"));
      if (!consultationMatch[2]) return route.fulfill(json(consultation));
      if (method === "POST") {
        const sent = { id: `cm-${consultationMessages.length}`, senderAccountId: PATIENT_ID, body: body.body, createdAt: iso(0) };
        consultationMessages.push(sent);
        return route.fulfill(json(sent));
      }
      return route.fulfill(json(page0(consultationMessages)));
    }
    if (appointmentMatch && method === "PATCH") {
      const appointment = data.appointments.find((item) => item.id === appointmentMatch[1])!;
      if (body.action === "CANCEL") appointment.status = "CANCELLED";
      if (body.action === "ACCEPT_RESCHEDULE") {
        appointment.status = "CONFIRMED";
        appointment.startsAt = appointment.proposedStartsAt!;
        appointment.endsAt = appointment.proposedEndsAt!;
        appointment.proposedStartsAt = null;
        appointment.proposedEndsAt = null;
        appointment.rescheduleRequestedBy = null;
      }
      if (body.action === "DECLINE_RESCHEDULE") {
        appointment.rescheduleRequestedBy = null;
        appointment.status = "CONFIRMED";
        appointment.proposedStartsAt = null;
        appointment.proposedEndsAt = null;
      }
      return route.fulfill(json(appointment));
    }
    if (path === "/notifications") return route.fulfill(json(page0(data.notifications)));
    const readMatch = path.match(/^\/notifications\/([^/]+)\/read$/);
    if (readMatch) {
      const item = data.notifications.find((notification) => notification.id === readMatch[1])!;
      (item as { readAt: string | null }).readAt = iso(0);
      return route.fulfill(json(item));
    }
    const searchable = (r: { requestedService: string; locationDescription: string | null }) => `${r.requestedService} ${r.locationDescription ?? ""}`;
    if (path === "/lab-requests" && method === "GET") {
      if (!url.searchParams.has("page")) return route.fulfill(json(data.labRequests));
      return route.fulfill(json(pageOf(filterQueue(data.labRequests, url, searchable), url)));
    }
    if (path === "/lab-requests" && method === "POST") {
      const created = { id: `req-${Date.now()}`, requestType: "LABORATORY", requestedService: body.testDescription, locationDescription: null, status: "OPEN", scheduledAt: null, createdAt: iso(0), updatedAt: iso(0) };
      data.labRequests.push(created);
      return route.fulfill(json(created));
    }
    if (path === "/home-care-requests" && method === "GET") {
      if (!url.searchParams.has("page")) return route.fulfill(json(data.homeCare));
      return route.fulfill(json(pageOf(filterQueue(data.homeCare, url, searchable), url)));
    }
    const cancelMatch = path.match(/^\/(home-care|lab)-requests\/([^/]+)\/cancel$/);
    if (cancelMatch) {
      const list = cancelMatch[1] === "lab" ? data.labRequests : data.homeCare;
      const item = list.find((request) => request.id === cancelMatch[2])!;
      item.status = "CANCELLED";
      return route.fulfill(json(item));
    }
    if (path === "/patients/me/records") return route.fulfill(json(page0(data.records)));
    if (path === "/patients/me/lab-results") {
      if (!url.searchParams.has("page")) return route.fulfill(json(data.labResults));
      const size = Number(url.searchParams.get("size") ?? 20);
      const number = Number(url.searchParams.get("page"));
      const rows = data.labResults;
      return route.fulfill(json({ content: rows.slice(number * size, number * size + size), totalElements: rows.length, totalPages: Math.ceil(rows.length / size), number, size }));
    }
    if (path === "/patients/me/record-consents" && method === "GET") return route.fulfill(json(data.consents));
    if (path === "/patients/me/record-consents" && method === "POST") {
      const provider = data.providers.find((item) => item.id === body.providerId)!;
      const consent = { providerId: provider.id, providerName: provider.displayName, specialty: provider.specialty, status: "GRANTED", grantedAt: iso(0), revokedAt: null };
      data.consents = [consent];
      return route.fulfill(json(consent));
    }
    if (path === "/patients/me/medications" && method === "GET") {
      return route.fulfill(json(url.searchParams.has("page") ? pageOf(data.medications, url) : data.medications));
    }
    if (/^\/patients\/me\/medications\/[^/]+\/dose-logs$/.test(path) && method === "GET") return route.fulfill(json(pageOf([], url)));
    if (path === "/notifications/preferences") return route.fulfill(json(preferences(method, body)));
    if (path === "/patients/me/vitals" && method === "GET") return route.fulfill(json(page0(data.vitals)));
    if (path === "/conversations" && method === "GET") return route.fulfill(json(data.conversations));
    const messagesMatch = path.match(/^\/conversations\/([^/]+)\/messages$/);
    if (messagesMatch && method === "GET") return route.fulfill(json(page0(data.messages)));
    if (messagesMatch && method === "POST") {
      const sent = { id: `m-${Date.now()}`, conversationId: messagesMatch[1], senderAccountId: PATIENT_ID, body: body.body, createdAt: iso(0) };
      data.messages.push(sent);
      return route.fulfill(json(sent));
    }
    if (path === "/providers") return route.fulfill(json(page0(data.providers)));
    if (path === "/providers/specialties") return route.fulfill(json([]));
    if (/^\/providers\/[^/]+$/.test(path) && path !== "/providers/me") {
      const found = data.providers.find((provider) => path === `/providers/${provider.id}`);
      return found ? route.fulfill(json(found)) : route.fulfill(problem(404, "Provider not found"));
    }
    if (/^\/providers\/[^/]+\/availability$/.test(path)) return route.fulfill(json(page0([])));
    return route.fulfill(problem(404, `Unstubbed ${method} ${path}`));
  });

  return Object.assign(log, {
    data,
    expireSession: () => {
      expired = true;
    },
  });
}
