import { afterEach, describe, expect, it, vi } from "vitest";
import { homeCareApi } from "@/features/home-care/home-care-api";
import { labApi } from "@/features/laboratory/lab-api";

const response = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 25,
};

describe("partner queue page APIs", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("requests home-care pages with server-side search and status filters", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json(response));
    vi.stubGlobal("fetch", fetchMock);

    await homeCareApi.requestsPage({ page: 2, size: 25, status: "OPEN", search: " Sinkor " });

    const url = new URL(String(fetchMock.mock.calls[0][0]), "http://localhost");
    expect(url.pathname).toBe("/api/v1/partner/service-requests");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      type: "HOME_CARE",
      page: "2",
      size: "25",
      status: "OPEN",
      search: "Sinkor",
    });
  });

  it("requests laboratory pages and omits unset filters", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => Response.json(response));
    vi.stubGlobal("fetch", fetchMock);

    await labApi.requestsPage({ page: 0, size: 25, search: "   " });

    const url = new URL(String(fetchMock.mock.calls[0][0]), "http://localhost");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      type: "LABORATORY",
      page: "0",
      size: "25",
    });
  });
});
