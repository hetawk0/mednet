import { afterEach, describe, expect, it, vi } from "vitest";
import { providerApi } from "./provider-api";

describe("provider history page APIs", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("requests paginated patient results using the scoped patient route", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      Response.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 25 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await providerApi.pendingLabResultsPage("patient/id", 3, 25);

    const url = new URL(String(fetchMock.mock.calls[0][0]), "http://localhost");
    expect(url.pathname).toBe("/api/v1/patients/patient%2Fid/lab-results/pending");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "3", size: "25" });
  });
});
