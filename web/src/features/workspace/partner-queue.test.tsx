import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { HomeCareRequest } from "@/features/home-care/home-care-api";
import { VisitQueue } from "@/features/home-care/visits";
import { OrderQueue } from "@/features/laboratory/orders";

const api = vi.hoisted(() => ({
  homeCareRequests: vi.fn(),
  labRequests: vi.fn(),
  push: vi.fn(),
}));

vi.mock("next/navigation", async () => {
  const React = await import("react");
  return {
    useRouter: () => ({
      push: (href: string) => {
        api.push(href);
        window.history.pushState({}, "", href);
        window.dispatchEvent(new PopStateEvent("popstate"));
      },
    }),
    useSearchParams: () => {
      const [, refresh] = React.useReducer((count: number) => count + 1, 0);
      React.useEffect(() => {
        window.addEventListener("popstate", refresh);
        return () => window.removeEventListener("popstate", refresh);
      }, []);
      return new URLSearchParams(window.location.search);
    },
  };
});

vi.mock("@/features/home-care/home-care-api", () => ({
  homeCareApi: {
    requestsPage: api.homeCareRequests,
    schedule: vi.fn(),
    startVisit: vi.fn(),
    completeVisit: vi.fn(),
  },
}));

vi.mock("@/features/home-care/home-care-workspace", () => ({
  useHomeCare: () => ({ replaceRequest: vi.fn(), reloadRequests: vi.fn() }),
}));

vi.mock("@/features/laboratory/lab-api", () => ({
  LAB_LIMITS: { resultSummary: 9700 },
  labApi: {
    requestsPage: api.labRequests,
    startProcessing: vi.fn(),
    resultsPage: vi.fn(),
    submitResult: vi.fn(),
  },
}));

vi.mock("@/features/laboratory/laboratory-workspace", () => ({
  useLaboratory: () => ({ latestResult: () => undefined, replaceRequest: vi.fn(), reloadRequests: vi.fn(), addResult: vi.fn() }),
}));

const homeRequest: HomeCareRequest = {
  id: "3f2a9c1e-55b0-4d4e-9a37-0c1d2e3f4a5b",
  requestType: "HOME_CARE",
  requestedService: "Wound dressing",
  locationDescription: "Sinkor",
  status: "OPEN",
  scheduledAt: null,
  createdAt: "2026-10-10T09:00:00Z",
  updatedAt: "2026-10-11T09:00:00Z",
};

const paged = <T,>(content: T[], number = 0, totalElements = content.length, totalPages = 1) => ({
  content,
  number,
  size: 25,
  totalElements,
  totalPages,
});

beforeEach(() => {
  api.homeCareRequests.mockReset();
  api.labRequests.mockReset();
  api.push.mockReset();
  window.history.replaceState({}, "", "/");
});

describe("partner request queues", () => {
  it("shows a loading state while a server page is pending", async () => {
    let resolvePage!: (value: ReturnType<typeof paged<HomeCareRequest>>) => void;
    api.homeCareRequests.mockReturnValue(
      new Promise((resolve) => {
        resolvePage = resolve;
      }),
    );
    render(<VisitQueue />);

    expect(await screen.findByText("Loading assigned home-care visits")).toBeInTheDocument();
    resolvePage(paged([homeRequest]));
    expect(await screen.findAllByText("Wound dressing")).not.toHaveLength(0);
  });

  it("sends server filters, keeps them while paging, and resets to page zero when filters change", async () => {
    window.history.replaceState({}, "", "/home-care/visits?search=Sinkor&status=OPEN&page=2");
    api.homeCareRequests.mockImplementation(({ page }: { page: number }) => Promise.resolve(paged([homeRequest], page, 60, 3)));
    const user = userEvent.setup();

    render(<VisitQueue />);
    expect(await screen.findAllByText("Wound dressing")).not.toHaveLength(0);
    expect(api.homeCareRequests).toHaveBeenNthCalledWith(
      1,
      { page: 1, size: 25, status: "OPEN", search: "Sinkor" },
      expect.any(AbortSignal),
    );

    await user.click(screen.getByRole("button", { name: "Next page" }));
    await waitFor(() => expect(api.homeCareRequests).toHaveBeenCalledTimes(2));
    expect(api.homeCareRequests).toHaveBeenNthCalledWith(
      2,
      { page: 2, size: 25, status: "OPEN", search: "Sinkor" },
      expect.any(AbortSignal),
    );
    expect(new URLSearchParams(window.location.search).get("page")).toBe("3");

    await user.selectOptions(screen.getByRole("combobox", { name: "Visit status" }), "RESOLVED");
    await waitFor(() => expect(api.homeCareRequests).toHaveBeenCalledTimes(3));
    expect(api.homeCareRequests).toHaveBeenNthCalledWith(
      3,
      { page: 0, size: 25, status: "RESOLVED", search: "Sinkor" },
      expect.any(AbortSignal),
    );
    expect(new URLSearchParams(window.location.search).get("page")).toBeNull();

    const search = screen.getByRole("searchbox", { name: "Search visits" });
    await user.clear(search);
    await user.type(search, "near clinic");
    await user.click(screen.getByRole("button", { name: "Search" }));
    await waitFor(() => expect(api.homeCareRequests).toHaveBeenCalledTimes(4));
    expect(api.homeCareRequests).toHaveBeenNthCalledWith(
      4,
      { page: 0, size: 25, status: "RESOLVED", search: "near clinic" },
      expect.any(AbortSignal),
    );
    expect(new URLSearchParams(window.location.search).get("status")).toBe("RESOLVED");
    expect(new URLSearchParams(window.location.search).get("page")).toBeNull();
  });

  it("shows a retry after a queue failure and a server-filtered empty state", async () => {
    window.history.replaceState({}, "", "/laboratory/orders?search=platelet&status=OPEN&page=4");
    api.labRequests
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(paged([], 0, 0, 0));
    const user = userEvent.setup();

    render(<OrderQueue />);
    expect(await screen.findByText("Your assigned orders could not be loaded")).toBeInTheDocument();
    expect(api.labRequests).toHaveBeenNthCalledWith(
      1,
      { page: 3, size: 25, status: "OPEN", search: "platelet" },
      expect.any(AbortSignal),
    );

    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("No orders match these filters")).toBeInTheDocument();
    expect(api.labRequests).toHaveBeenCalledTimes(2);
  });

  it("shows the unfiltered empty state after a successful empty home-care response", async () => {
    api.homeCareRequests.mockResolvedValue(paged([], 0, 0, 0));
    render(<VisitQueue />);

    expect(await screen.findByText("No home-care requests are assigned to you")).toBeInTheDocument();
  });
});
