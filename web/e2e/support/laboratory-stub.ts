import type { Page, Request } from "@playwright/test";
import { filterQueue, pageOf, preferencesStub } from "./paging";

/**
 * In-browser stand-in for the laboratory partner API (shapes from PartnerRequestDetails and
 * LaboratoryResultDetails), enforcing the same transitions: OPEN → IN_PROGRESS by the laboratory, a
 * result only while IN_PROGRESS and only one PENDING_REVIEW at a time. Real integration (including the
 * doctor and patient stages) is covered by laboratory.live.spec.ts.
 */
const hour = 3_600_000;
const iso = (offsetHours: number) => new Date(Date.now() + offsetHours * hour).toISOString();
const json = (body: unknown, status = 200) => ({ status, contentType: "application/json", body: JSON.stringify(body) });
const problem = (status: number, message: string) =>
  json({ success: false, error: { code: `HTTP_${status}`, message, details: [] }, detail: message }, status);

export type LaboratoryStubOptions = {
  role?: string;
  signedOut?: boolean;
  empty?: boolean;
  /** Request IDs that the server reports as cancelled when the laboratory acts on them (stale view). */
  cancelledElsewhere?: string[];
  /** Request IDs that already have a result awaiting review on the server. */
  pendingOnServer?: string[];
  /** Request IDs whose latest result a doctor returned. */
  returnedOnServer?: string[];
};

export type LaboratoryStubLog = {
  requests: Array<{ method: string; path: string; body: unknown; csrf: string | null }>;
  expireSession: () => void;
};

export const LAB_IDS = {
  open: "3f2a9c1e-0000-4000-8000-000000000001",
  processing: "7b4d2e10-0000-4000-8000-000000000002",
  released: "9c8e7f20-0000-4000-8000-000000000003",
  cancelled: "a1b2c3d4-0000-4000-8000-000000000004",
};

export async function stubLaboratoryApi(page: Page, options: LaboratoryStubOptions = {}): Promise<LaboratoryStubLog> {
  let expired = false;
  let signedOut = options.signedOut ?? false;
  const pending = new Set(options.pendingOnServer ?? []);
  const request = (id: string, requestedService: string, status: string, created: number) => ({
    id, requestType: "LABORATORY", requestedService, locationDescription: null, status, scheduledAt: null, createdAt: iso(created), updatedAt: iso(created / 2),
  });
  const requests = options.empty
    ? []
    : [
        request(LAB_IDS.open, "Full blood count", "OPEN", -2),
        request(LAB_IDS.processing, "Fasting blood glucose", "IN_PROGRESS", -20),
        request(LAB_IDS.released, "Lipid profile", "RESOLVED", -80),
        request(LAB_IDS.cancelled, "Malaria rapid diagnostic test", "CANCELLED", -90),
      ];
  const results: Array<{ id: string; requestId: string; summary: string; status: string; createdAt: string; reviewedAt: string | null }> = [];
  const addResult = (requestId: string, status: string, hoursAgo: number) =>
    results.unshift({ id: `res-${results.length + 1}`, requestId, summary: "Synthetic result", status, createdAt: iso(-hoursAgo), reviewedAt: status === "PENDING_REVIEW" ? null : iso(-hoursAgo / 2) });
  if (!options.empty) addResult(LAB_IDS.released, "RELEASED", 70);
  for (const id of options.returnedOnServer ?? []) addResult(id, "RETURNED", 6);
  for (const id of options.pendingOnServer ?? []) addResult(id, "PENDING_REVIEW", 2);
  const log: LaboratoryStubLog = { requests: [], expireSession: () => (expired = true) };
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
      return signedOut ? route.fulfill(problem(401, "Sign-in required")) : route.fulfill(json({ email: "lab@mednet.test", role: options.role ?? "LABORATORY" }));
    }
    if (path.startsWith("/partner") && (options.role ?? "LABORATORY") !== "LABORATORY") return route.fulfill(problem(403, "Partner account access is not allowed"));

    if (path === "/partner/lab-results") {
      if ((options.role ?? "LABORATORY") !== "LABORATORY") return route.fulfill(problem(403, "Laboratory staff access is not allowed"));
      const status = url.searchParams.get("status");
      const matching = results.filter((result) => !status || result.status === status);
      if (!url.searchParams.has("page")) return route.fulfill(json(matching));
      const size = Number(url.searchParams.get("size") ?? 20);
      const number = Number(url.searchParams.get("page"));
      return route.fulfill(
        json({ content: matching.slice(number * size, number * size + size), totalElements: matching.length, totalPages: Math.ceil(matching.length / size), number, size }),
      );
    }
    if (path === "/partner/service-requests") {
      if (url.searchParams.get("type") !== "LABORATORY") return route.fulfill(problem(403, "Partner account access is not allowed"));
      // Each order carries its newest result's review state (Phase 5D), as the API does.
      const withResults = requests.map((request) => {
        const latest = results.find((result) => result.requestId === request.id);
        return { ...request, latestResult: latest ? { id: latest.id, status: latest.status, createdAt: latest.createdAt, reviewedAt: latest.reviewedAt } : null };
      });
      if (!url.searchParams.has("page")) return route.fulfill(json(withResults));
      const matching = filterQueue(withResults, url, (r) => `${r.requestedService} ${r.locationDescription ?? ""}`);
      return route.fulfill(json(pageOf(matching, url)));
    }
    if (path === "/notifications/preferences") return route.fulfill(json(preferences(method, body)));
    const match = path.match(/^\/partner\/service-requests\/([^/]+)\/(status|lab-result)$/);
    if (match) {
      const target = requests.find((r) => r.id === match[1]);
      if (!target) return route.fulfill(problem(404, "Service request not found"));
      if (options.cancelledElsewhere?.includes(target.id)) {
        target.status = "CANCELLED";
        return route.fulfill(problem(409, "Cannot move LABORATORY request from CANCELLED to IN_PROGRESS"));
      }
      if (match[2] === "status") {
        if (target.status !== "OPEN" || body.status !== "IN_PROGRESS") return route.fulfill(problem(409, `Cannot move LABORATORY request from ${target.status} to ${body.status}`));
        target.status = "IN_PROGRESS";
        target.updatedAt = iso(0);
        return route.fulfill(json(target));
      }
      if (target.status !== "IN_PROGRESS") return route.fulfill(problem(409, "Laboratory request is not in progress"));
      if (pending.has(target.id)) return route.fulfill(problem(409, "A result is already awaiting provider review"));
      pending.add(target.id);
      addResult(target.id, "PENDING_REVIEW", 0);
      results[0].summary = body.summary;
      return route.fulfill(json(results[0]));
    }
    return route.fulfill(problem(404, `Unstubbed ${method} ${path}`));
  });
  return log;
}
