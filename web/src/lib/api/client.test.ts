import { beforeEach, describe, expect, it, vi } from "vitest";
import { patientApi } from "@/features/patient/patient-api";
import { providerApi } from "@/features/provider/provider-api";
import { apiRequest, buildApiUrl } from "./client";
import { ApiRequestError } from "./errors";
import { getSession } from "./session";

const fetchMock = vi.fn<typeof fetch>();

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("buildApiUrl", () => {
  it("repeats a key for array values and skips empty items", () => {
    expect(buildApiUrl("/admin/service-requests", { type: "HOME_CARE", status: ["OPEN", undefined, "IN_PROGRESS"], page: 0 })).toBe(
      "/api/v1/admin/service-requests?type=HOME_CARE&status=OPEN&status=IN_PROGRESS&page=0",
    );
  });

  it("prefixes /api/v1 and drops empty query values", () => {
    expect(buildApiUrl("appointments", { page: 0, size: 1, status: undefined })).toBe(
      "/api/v1/appointments?page=0&size=1",
    );
  });
});

describe("apiRequest", () => {
  it("keeps patient and provider message pagination on the authenticated same-origin API", async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }))
      .mockResolvedValueOnce(json(200, { content: [], totalElements: 0, totalPages: 0, number: 2, size: 100 }));
    const patientSignal = new AbortController().signal;
    const providerSignal = new AbortController().signal;

    await patientApi.messages("conversation/one", 1, 100, patientSignal);
    await providerApi.messages("conversation/two", 2, 100, providerSignal);

    expect(fetchMock.mock.calls).toEqual([
      [
        "/api/v1/conversations/conversation%2Fone/messages?page=1&size=100",
        expect.objectContaining({ method: "GET", credentials: "same-origin", signal: patientSignal }),
      ],
      [
        "/api/v1/conversations/conversation%2Ftwo/messages?page=2&size=100",
        expect.objectContaining({ method: "GET", credentials: "same-origin", signal: providerSignal }),
      ],
    ]);
  });

  it("sends GET with the session cookie and no CSRF round trip", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { items: [] }));

    await expect(apiRequest("/appointments")).resolves.toEqual({ items: [] });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/appointments");
    expect(init).toMatchObject({ method: "GET", credentials: "same-origin", cache: "no-store" });
  });

  it("fetches a CSRF token and sends it as the named header on writes", async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { headerName: "X-XSRF-TOKEN", token: "abc" }))
      .mockResolvedValueOnce(json(200, { id: 7 }));

    await apiRequest("/patients/me/profile", { method: "PUT", body: { phone: "1" } });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/v1/patients/me/profile");
    expect(init?.headers).toMatchObject({
      "X-XSRF-TOKEN": "abc",
      "Content-Type": "application/json",
    });
    expect(init?.body).toBe(JSON.stringify({ phone: "1" }));
  });

  it("resolves undefined for 204 responses", async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { headerName: "X-XSRF-TOKEN", token: "abc" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(apiRequest("/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });

  it("maps the backend error body to ApiRequestError", async () => {
    fetchMock.mockResolvedValueOnce(
      json(422, {
        success: false,
        error: {
          code: "VALIDATION_FAILED",
          message: "Check the highlighted fields.",
          details: [{ field: "phone", message: "Enter a complete mobile number." }],
        },
        detail: "Check the highlighted fields.",
        meta: { requestId: "req-1" },
      }),
    );

    const error = await apiRequest("/appointments").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({
      status: 422,
      code: "VALIDATION_FAILED",
      message: "Check the highlighted fields.",
      requestId: "req-1",
      details: [{ field: "phone", message: "Enter a complete mobile number." }],
    });
  });

  it("falls back to a readable message when the body is not JSON", async () => {
    fetchMock.mockResolvedValueOnce(new Response("<html>", { status: 403 }));

    const error = (await apiRequest("/admin/overview").catch((e: unknown) => e)) as ApiRequestError;

    expect(error.isForbidden).toBe(true);
    expect(error.code).toBe("HTTP_403");
    expect(error.message).toBe("You do not have permission to do that.");
  });

  it("explains a rate-limited request even when a proxy answers without the API body", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 429 }));

    const error = (await apiRequest("/auth/session").catch((e: unknown) => e)) as ApiRequestError;

    expect(error.status).toBe(429);
    expect(error.message).toBe("Too many attempts. Please wait and try again later.");
  });

  it("sends form-encoded bodies for Spring form login, with CSRF", async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { headerName: "X-XSRF-TOKEN", token: "csrf-1" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await apiRequest("/auth/login", { method: "POST", form: { email: "p@b.lr", password: "a&b=c" } });

    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/v1/auth/login");
    expect(init?.headers).toMatchObject({
      "Content-Type": "application/x-www-form-urlencoded",
      "X-XSRF-TOKEN": "csrf-1",
    });
    expect(String(init?.body)).toBe("email=p%40b.lr&password=a%26b%3Dc");
  });

  it("uses the message from auth endpoints that answer errors without the standard envelope", async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, { token: null, message: "The code is invalid or expired. Request a new code." }),
    );

    const error = (await apiRequest("/auth/reset-password/verify").catch((e: unknown) => e)) as ApiRequestError;

    expect(error.status).toBe(400);
    expect(error.code).toBe("HTTP_400");
    expect(error.message).toBe("The code is invalid or expired. Request a new code.");
  });

  it("reports network failures with status 0", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const error = (await apiRequest("/providers").catch((e: unknown) => e)) as ApiRequestError;

    expect(error.isNetworkFailure).toBe(true);
    expect(error.message).toBe("The MedNet API is unavailable. Try again shortly.");
  });
});

describe("getSession", () => {
  it("returns the session when signed in", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { email: "a@b.lr", role: "PATIENT" }));
    await expect(getSession()).resolves.toEqual({ email: "a@b.lr", role: "PATIENT" });
  });

  it("returns null on 401", async () => {
    fetchMock.mockResolvedValueOnce(
      json(401, { success: false, error: { code: "UNAUTHORIZED", message: "Sign-in required" } }),
    );
    await expect(getSession()).resolves.toBeNull();
  });

  it("rethrows other failures", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 500 }));
    await expect(getSession()).rejects.toBeInstanceOf(ApiRequestError);
  });
});
