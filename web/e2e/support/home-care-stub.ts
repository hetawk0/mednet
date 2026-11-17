import type { Page, Request } from "@playwright/test";
import { filterQueue, pageOf, preferencesStub } from "./paging";

/**
 * In-browser stand-in for the home-care partner API (shape from PartnerRequestDetails), enforcing the
 * same rules as PatientServiceRequestService: a visit time once, only while OPEN and in the future;
 * OPEN (scheduled) → IN_PROGRESS → RESOLVED. Real integration (patient, administrator, staff) is covered
 * by home-care.live.spec.ts.
 */
const hour = 3_600_000;
const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * hour).toISOString();
const json = (body: unknown, status = 200) => ({ status, contentType: "application/json", body: JSON.stringify(body) });
const problem = (status: number, message: string) =>
  json({ success: false, error: { code: `HTTP_${status}`, message, details: [] }, detail: message }, status);

export type HomeCareStubOptions = {
  role?: string;
  signedOut?: boolean;
  empty?: boolean;
  /** Request IDs the server reports as cancelled when staff act on them (stale view). */
  cancelledElsewhere?: string[];
};

export type HomeCareStubLog = {
  requests: Array<{ method: string; path: string; body: unknown; csrf: string | null }>;
  expireSession: () => void;
};

export const HOME_CARE_IDS = {
  unscheduled: "4e1d2c3b-0000-4000-8000-000000000011",
  scheduled: "5f2e3d4c-0000-4000-8000-000000000012",
  inProgress: "6a3f4e5d-0000-4000-8000-000000000013",
  completed: "7b4a5f6e-0000-4000-8000-000000000014",
  cancelled: "8c5b6a7f-0000-4000-8000-000000000015",
};

export async function stubHomeCareApi(page: Page, options: HomeCareStubOptions = {}): Promise<HomeCareStubLog> {
  let expired = false;
  let signedOut = options.signedOut ?? false;
  const role = options.role ?? "HOME_CARE";
  const request = (id: string, requestedService: string, locationDescription: string, status: string, created: number, scheduledAt: string | null) => ({
    id, requestType: "HOME_CARE", requestedService, locationDescription, status, scheduledAt, createdAt: iso(created), updatedAt: iso(created / 2),
  });
  const requests = options.empty
    ? []
    : [
        request(HOME_CARE_IDS.unscheduled, "Wound dressing change", "Sinkor, 12th Street, blue gate", "OPEN", -3, null),
        request(HOME_CARE_IDS.scheduled, "Blood pressure check", "Paynesville, Red Light area", "OPEN", -30, iso(26)),
        request(HOME_CARE_IDS.inProgress, "Post-operative check", "Congo Town, near the school", "IN_PROGRESS", -50, iso(-1)),
        request(HOME_CARE_IDS.completed, "Medication support", "Mamba Point", "RESOLVED", -90, iso(-60)),
        request(HOME_CARE_IDS.cancelled, "Mobility assistance", "Old Road", "CANCELLED", -100, null),
      ];
  const log: HomeCareStubLog = { requests: [], expireSession: () => (expired = true) };
  const preferences = preferencesStub();

  await page.route("**/api/v1/**", async (route, req: Request) => {
    const url = new URL(req.url());
    const path = url.pathname.replace("/api/v1", "");
    const method = req.method();
    const body = req.postData() ? JSON.parse(req.postData()!) : undefined;
    if (method !== "GET") log.requests.push({ method, path, body, csrf: req.headers()["x-xsrf-token"] ?? null });

    if (path === "/auth/csrf") return route.fulfill(json({ headerName: "X-XSRF-TOKEN", token: "test-token" }));
    if (path === "/auth/google/status") return route.fulfill(json({ enabled: false }));
    if (path === "/auth/logout") {
      signedOut = true;
      return route.fulfill({ status: 204 });
    }
    if (expired || (signedOut && path !== "/auth/session")) return route.fulfill(problem(401, "Sign-in required"));
    if (path === "/auth/session") {
      return signedOut ? route.fulfill(problem(401, "Sign-in required")) : route.fulfill(json({ email: "care@mednet.test", role }));
    }
    if (path.startsWith("/partner") && role !== "HOME_CARE") return route.fulfill(problem(403, "Partner account access is not allowed"));
    if (path === "/partner/service-requests") {
      if (url.searchParams.get("type") !== "HOME_CARE") return route.fulfill(problem(403, "Partner account access is not allowed"));
      if (!url.searchParams.has("page")) return route.fulfill(json(requests));
      const matching = filterQueue(requests, url, (r) => `${r.requestedService} ${r.locationDescription ?? ""}`);
      return route.fulfill(json(pageOf(matching, url)));
    }
    if (path === "/notifications/preferences") return route.fulfill(json(preferences(method, body)));
    const match = path.match(/^\/partner\/service-requests\/([^/]+)\/(status|schedule)$/);
    if (match && method === "PATCH") {
      const target = requests.find((r) => r.id === match[1]);
      if (!target) return route.fulfill(problem(404, "Service request not found"));
      if (options.cancelledElsewhere?.includes(target.id)) {
        target.status = "CANCELLED";
        return route.fulfill(
          problem(409, match[2] === "schedule" ? "Only unscheduled open home-care requests can be scheduled" : "Cannot move HOME_CARE request from CANCELLED to IN_PROGRESS"),
        );
      }
      if (match[2] === "schedule") {
        if (target.status !== "OPEN" || target.scheduledAt) return route.fulfill(problem(409, "Only unscheduled open home-care requests can be scheduled"));
        if (!(new Date(body.scheduledAt).getTime() > Date.now())) return route.fulfill(problem(400, "The home-care appointment time must be in the future"));
        target.scheduledAt = body.scheduledAt;
        target.updatedAt = iso(0);
        return route.fulfill(json(target));
      }
      const starting = target.status === "OPEN" && body.status === "IN_PROGRESS" && target.scheduledAt;
      const completing = target.status === "IN_PROGRESS" && body.status === "RESOLVED";
      if (!starting && !completing) return route.fulfill(problem(409, `Cannot move HOME_CARE request from ${target.status} to ${body.status}`));
      target.status = body.status;
      target.updatedAt = iso(0);
      return route.fulfill(json(target));
    }
    return route.fulfill(problem(404, `Unstubbed ${method} ${path}`));
  });
  return log;
}
