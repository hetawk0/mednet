import type { Page, Request } from "@playwright/test";
import { pageOf, preferencesStub } from "./paging";

/**
 * In-browser stand-in for the provider APIs (shapes from the Spring records). Real integration is
 * covered by provider.live.spec.ts.
 */
const hour = 3_600_000;
const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * hour).toISOString();
const PROVIDER_ID = "prov-cole";
const PROVIDER_ACCOUNT = "acc-cole";

export type ProviderStubOptions = {
  role?: string;
  /** Application status, or "none" for 404. */
  application?: "APPROVED" | "PENDING" | "REJECTED" | "SUSPENDED" | "none";
  /** Stored account type reported by /auth/session (PROVIDER with role PATIENT = provider sign-up awaiting approval). */
  accountType?: string;
  empty?: boolean;
};

export type ProviderStubLog = {
  requests: Array<{ method: string; path: string; body: unknown; csrf: string | null }>;
  /** Every GET path requested, to prove which endpoints a view did (not) call. */
  reads: string[];
  expireSession: () => void;
  /** An administrator's decision: the application changes and, as the API does, the session ends. */
  review: (status: "APPROVED" | "REJECTED" | "SUSPENDED") => void;
};

const page0 = <T,>(content: T[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0, size: 100 });
const json = (body: unknown, status = 200) => ({ status, contentType: "application/json", body: JSON.stringify(body) });
const problem = (status: number, message: string) => json({ success: false, error: { code: `HTTP_${status}`, message, details: [] } }, status);

function appointment(id: string, status: string, startsIn: number, patient: [id: string, name: string], extra: Record<string, unknown> = {}) {
  return {
    id,
    patientAccountId: patient[0],
    status,
    patientName: patient[1],
    providerName: "Dr. Mariama Cole",
    providerId: PROVIDER_ID,
    providerSpecialty: "General medicine",
    rescheduleRequestedBy: null as string | null,
    consultationMode: "TEXT",
    startsAt: iso(startsIn),
    endsAt: iso(startsIn + 0.5),
    proposedStartsAt: null as string | null,
    proposedEndsAt: null as string | null,
    createdAt: iso(-48),
    updatedAt: iso(-24),
    ...extra,
  };
}

const JAMES: [string, string] = ["pat-james", "James Brown"];
const EVELYN: [string, string] = ["pat-evelyn", "Evelyn Johnson"];
const NEW: [string, string] = ["pat-new", "Joseph Flomo"];

export async function stubProviderApi(page: Page, options: ProviderStubOptions = {}): Promise<ProviderStubLog> {
  let expired = false;
  let application = options.application ?? "APPROVED";
  const appointments = options.empty
    ? []
    : [
        appointment("apt-request", "PENDING", 30, NEW),
        appointment("apt-today", "CONFIRMED", 2, JAMES),
        appointment("apt-moved", "RESCHEDULE_REQUESTED", 50, EVELYN, {
          rescheduleRequestedBy: "PATIENT",
          consultationMode: "IN_PERSON",
          proposedStartsAt: iso(74),
          proposedEndsAt: iso(74.5),
        }),
        appointment("apt-ended", "CONFIRMED", -5, JAMES),
        appointment("apt-done", "COMPLETED", -100, EVELYN),
      ];
  let slots = options.empty ? [] : [
    { id: "slot-1", startsAt: iso(80), endsAt: iso(80.5), consultationMode: "TEXT" },
    { id: "slot-2", startsAt: iso(104), endsAt: iso(104.5), consultationMode: "IN_PERSON" },
  ];
  const records = [
    { id: "rec-1", category: "ENCOUNTER_SUMMARY", title: "Follow-up consultation", clinicalCode: null, summary: "Discussed progress.", effectiveAt: iso(-99), createdAt: iso(-99), amendsRecordId: null, authorName: "Dr. Mariama Cole" },
  ];
  let pending = [{ id: "lab-1", requestId: "req-1", summary: "Complete blood count: values reported by the laboratory.", status: "PENDING_REVIEW", createdAt: iso(-6), reviewedAt: null }];
  const consultations: Record<string, { id: string; appointmentId: string; status: string; openedAt: string; endedAt: string | null }> = {};
  const consultationMessages: Array<{ id: string; senderAccountId: string; body: string; createdAt: string }> = [];
  const messages = [{ id: "m-1", conversationId: "conv-1", senderAccountId: "pat-james", body: "Can I take the medicine with food?", createdAt: iso(-3) }];
  type StubNotification = { id: string; eventType: string; title: string; resourceType: string; resourceId: string; createdAt: string; readAt: string | null };
  const notifications: StubNotification[] = options.empty
    ? []
    : [{ id: "n-1", eventType: "APPOINTMENT", title: "New appointment request", resourceType: "appointment", resourceId: "apt-request", createdAt: iso(-1), readAt: null }];
  // AdminWorkflowService notifies the applicant of approvals and rejections (not suspensions).
  const decide = (status: string) => {
    if (status !== "APPROVED" && status !== "REJECTED") return;
    notifications.unshift({
      id: `n-decision-${notifications.length + 1}`,
      eventType: "PROVIDER_APPLICATION",
      title: status === "APPROVED" ? "Your provider application was approved" : "Your provider application was not approved",
      resourceType: "provider",
      resourceId: PROVIDER_ID,
      createdAt: iso(-0.5),
      readAt: null,
    });
  };
  // Applicant views start with the decision already on record; approved-provider fixtures keep their feed.
  if (options.role === "PATIENT") decide(application);
  const log: ProviderStubLog = {
    requests: [],
    reads: [],
    expireSession: () => (expired = true),
    review: (status) => {
      application = status;
      decide(status);
      expired = true;
    },
  };
  const preferences = preferencesStub();

  await page.route("**/api/v1/**", async (route, request: Request) => {
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData()!) : undefined;
    if (method !== "GET") log.requests.push({ method, path, body, csrf: request.headers()["x-xsrf-token"] ?? null });
    else log.reads.push(path);

    if (path === "/auth/csrf") return route.fulfill(json({ headerName: "X-XSRF-TOKEN", token: "test-token" }));
    if (path === "/auth/google/status") return route.fulfill(json({ enabled: false }));
    if (expired) return route.fulfill(problem(401, "Sign-in required"));
    if (path === "/auth/session") {
      // By default the stored account type matches the role (a patient who applied stays a PATIENT account).
      const role = options.role ?? "PROVIDER";
      return route.fulfill(json({ email: "cole@mednet.test", role, accountType: options.accountType ?? role }));
    }
    if (path === "/auth/logout") {
      expired = true;
      return route.fulfill({ status: 204 });
    }
    if (path === "/notifications/preferences") return route.fulfill(json(preferences(method, body)));

    if (path === "/providers/me/application") {
      if (application === "none") return route.fulfill(problem(404, "Provider application not found"));
      return route.fulfill(json({ id: PROVIDER_ID, displayName: "Dr. Mariama Cole", specialty: "General medicine", credentialReference: "LMDC-1234", status: application, createdAt: iso(-500), reviewedAt: application === "PENDING" ? null : iso(-400) }));
    }
    if (path === "/providers/applications" && method === "POST") {
      application = "PENDING";
      return route.fulfill(json({ id: PROVIDER_ID, ...body, status: "PENDING", createdAt: iso(0), reviewedAt: null }));
    }
    // Recipient-scoped feed, filterable by eventType like the API; readable before approval.
    if (path === "/notifications") {
      const eventType = url.searchParams.get("eventType");
      return route.fulfill(json(page0(notifications.filter((n) => !eventType || n.eventType === eventType))));
    }
    const readMatch = path.match(/^\/notifications\/([^/]+)\/read$/);
    if (readMatch && method === "PATCH") {
      const item = notifications.find((n) => n.id === readMatch[1]);
      if (!item) return route.fulfill(problem(404, "Notification not found"));
      item.readAt = iso(0);
      return route.fulfill(json(item));
    }
    if (application !== "APPROVED") return route.fulfill(problem(404, "Approved provider not found"));

    if (path === `/providers/${PROVIDER_ID}/availability`) return route.fulfill(json(page0(slots)));
    if (path === "/providers/me/availability" && method === "POST") {
      const overlaps = slots.some((s) => s.startsAt < body.endsAt && s.endsAt > body.startsAt);
      if (overlaps) return route.fulfill(problem(409, "Availability slots may not overlap"));
      const created = { id: `slot-${slots.length + 1}`, ...body };
      slots = [...slots, created].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
      return route.fulfill(json(created));
    }
    const closeMatch = path.match(/^\/providers\/me\/availability\/([^/]+)$/);
    if (closeMatch && method === "DELETE") {
      if (closeMatch[1] === "slot-2") return route.fulfill(problem(409, "Reserved availability cannot be closed"));
      slots = slots.filter((s) => s.id !== closeMatch[1]);
      return route.fulfill({ status: 204 });
    }

    if (path === "/appointments") return route.fulfill(json(page0(appointments)));
    const apptMatch = path.match(/^\/appointments\/([^/]+)$/);
    if (apptMatch) {
      const item = appointments.find((a) => a.id === apptMatch[1]);
      if (!item) return route.fulfill(problem(404, "Appointment or participant not found"));
      if (method === "GET") return route.fulfill(json(item));
      const transitions: Record<string, string> = { ACCEPT: "CONFIRMED", DECLINE: "DECLINED", CANCEL: "CANCELLED", COMPLETE: "COMPLETED", ACCEPT_RESCHEDULE: "CONFIRMED", DECLINE_RESCHEDULE: "CONFIRMED", REQUEST_RESCHEDULE: "RESCHEDULE_REQUESTED" };
      if (body.action === "REQUEST_RESCHEDULE") {
        const slot = slots.find((s) => s.id === body.proposedAvailabilitySlotId)!;
        Object.assign(item, { proposedStartsAt: slot.startsAt, proposedEndsAt: slot.endsAt, rescheduleRequestedBy: "PROVIDER" });
        slots = slots.filter((s) => s.id !== slot.id);
      }
      if (body.action === "ACCEPT_RESCHEDULE") Object.assign(item, { startsAt: item.proposedStartsAt, endsAt: item.proposedEndsAt });
      if (body.action === "ACCEPT_RESCHEDULE" || body.action === "DECLINE_RESCHEDULE") Object.assign(item, { proposedStartsAt: null, proposedEndsAt: null, rescheduleRequestedBy: null });
      item.status = transitions[body.action];
      return route.fulfill(json(item));
    }
    const openMatch = path.match(/^\/appointments\/([^/]+)\/consultation$/);
    if (openMatch) {
      consultations[openMatch[1]] ??= { id: `con-${openMatch[1]}`, appointmentId: openMatch[1], status: "OPEN", openedAt: iso(0), endedAt: null };
      return route.fulfill(json(consultations[openMatch[1]]));
    }
    const conMatch = path.match(/^\/consultations\/([^/]+)(\/messages|\/end)?$/);
    if (conMatch) {
      const consultation = Object.values(consultations).find((c) => c.id === conMatch[1]);
      if (!consultation) return route.fulfill(problem(404, "Consultation not found"));
      if (conMatch[2] === "/end") return route.fulfill(json(Object.assign(consultation, { status: "ENDED", endedAt: iso(0) })));
      if (conMatch[2] === "/messages" && method === "POST") {
        const sent = { id: `cm-${consultationMessages.length}`, senderAccountId: PROVIDER_ACCOUNT, body: body.body, createdAt: iso(0) };
        consultationMessages.push(sent);
        return route.fulfill(json(sent));
      }
      if (conMatch[2] === "/messages") return route.fulfill(json(page0(consultationMessages)));
      return route.fulfill(json(consultation));
    }

    if (path === "/providers/me/lab-review-queue") {
      // Care patients: James has shared records and pending results; Evelyn has not shared (Phase 5D endpoint).
      const entries = options.empty
        ? []
        : [
            ...(pending.length ? [{ patientAccountId: JAMES[0], patientName: JAMES[1], recordAccess: true, pendingResults: pending.length, oldestPendingAt: pending[0].createdAt }] : []),
            { patientAccountId: EVELYN[0], patientName: EVELYN[1], recordAccess: false, pendingResults: 0, oldestPendingAt: null },
          ];
      return route.fulfill(
        json({ ...page0(entries), totalPendingResults: pending.length, patientsWithoutRecordAccess: options.empty ? 0 : 1 }),
      );
    }
    // Care patients: a CONFIRMED, COMPLETED or RESCHEDULE_REQUESTED appointment with this provider
    // (PatientCareAccess.CARE_STATUSES), sorted by name as ProviderService does.
    const carePatients = () =>
      [...new Map(
        appointments
          .filter((a) => ["CONFIRMED", "COMPLETED", "RESCHEDULE_REQUESTED"].includes(a.status))
          .map((a) => [a.patientAccountId, { patientAccountId: a.patientAccountId, name: a.patientName }]),
      ).values()].sort((a, b) => a.name.localeCompare(b.name));
    if (path === "/providers/me/patients") return route.fulfill(json(pageOf(carePatients(), url)));
    const carePatientMatch = path.match(/^\/providers\/me\/patients\/([^/]+)(\/appointments)?$/);
    if (carePatientMatch) {
      const patient = carePatients().find((p) => p.patientAccountId === carePatientMatch[1]);
      if (!carePatientMatch[2]) return route.fulfill(patient ? json(patient) : problem(404, "Patient not found"));
      if (!patient) return route.fulfill(problem(403, "A confirmed care relationship is required"));
      const history = appointments
        .filter((a) => a.patientAccountId === patient.patientAccountId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      return route.fulfill(json(pageOf(history, url)));
    }
    const patientMatch = path.match(/^\/patients\/([^/]+)\/(records|vitals|lab-results\/pending)$/);
    if (patientMatch) {
      // Evelyn has a care relationship but has not granted consent.
      if (patientMatch[1] === EVELYN[0]) return route.fulfill(problem(403, "Active patient consent is required"));
      if (patientMatch[1] === NEW[0]) return route.fulfill(problem(403, "A confirmed care relationship is required"));
      if (patientMatch[2] === "records" && method === "POST") {
        const created = { id: `rec-${records.length + 1}`, ...body, clinicalCode: body.clinicalCode ?? null, amendsRecordId: body.amendsRecordId ?? null, createdAt: iso(0), authorName: "Dr. Mariama Cole" };
        records.unshift(created);
        return route.fulfill(json(created));
      }
      if (patientMatch[2] === "records") return route.fulfill(json(page0(records)));
      if (patientMatch[2] === "vitals") return route.fulfill(json(page0([{ id: "v-1", metric: "Weight", value: 72.5, unit: "kg", recordedAt: iso(-20), source: "PATIENT_REPORTED" }])));
      return route.fulfill(json(url.searchParams.has("page") ? pageOf(pending, url) : pending));
    }
    const reviewMatch = path.match(/^\/lab-results\/([^/]+)\/review$/);
    if (reviewMatch) {
      const result = pending.find((r) => r.id === reviewMatch[1])!;
      pending = pending.filter((r) => r.id !== result.id);
      return route.fulfill(json({ ...result, status: body.action === "RELEASE" ? "RELEASED" : "RETURNED", reviewedAt: iso(0) }));
    }

    if (path === "/conversations") return route.fulfill(json(options.empty ? [] : [{ id: "conv-1", patientAccountId: "pat-james", providerId: PROVIDER_ID, providerDisplayName: "Dr. Mariama Cole", createdAt: iso(-90), updatedAt: iso(-3) }]));
    if (path === "/conversations/conv-1/messages" && method === "POST") {
      const sent = { id: `m-${messages.length + 1}`, conversationId: "conv-1", senderAccountId: PROVIDER_ACCOUNT, body: body.body, createdAt: iso(0) };
      messages.push(sent);
      return route.fulfill(json(sent));
    }
    if (path === "/conversations/conv-1/messages") return route.fulfill(json(page0(messages)));
    return route.fulfill(problem(404, `Unstubbed ${method} ${path}`));
  });
  return log;
}
