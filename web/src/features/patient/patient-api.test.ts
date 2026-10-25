import { afterEach, describe, expect, it, vi } from "vitest";
import { patientApi } from "./patient-api";

const page = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 25,
};

describe("patient history page APIs", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("requests filtered home-care pages and trims search text", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json(page));
    vi.stubGlobal("fetch", fetchMock);

    await patientApi.homeCareRequestsPage(2, 25, { status: "OPEN", search: " Sinkor " });

    const url = new URL(String(fetchMock.mock.calls[0][0]), "http://localhost");
    expect(url.pathname).toBe("/api/v1/home-care-requests");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: "2",
      size: "25",
      status: "OPEN",
      search: "Sinkor",
    });
  });

  it("requests paged medication dose history with an encoded schedule identifier", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json(page));
    vi.stubGlobal("fetch", fetchMock);

    await patientApi.doseLogs("schedule/id", 1, 20);

    const url = new URL(String(fetchMock.mock.calls[0][0]), "http://localhost");
    expect(url.pathname).toBe("/api/v1/patients/me/medications/schedule%2Fid/dose-logs");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "1", size: "20" });
  });
});
