import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { adminApi, type AuditEvent } from "./admin-api";
import { CsvExportButton } from "./csv-export-button";
import {
  CSV_EXPORT_PAGE_SIZE,
  csvCell,
  exportAllAuditEvents,
  exportAllPatientRequests,
  fetchAllPages,
  toCsv,
} from "./csv-export";
import * as csvExport from "./csv-export";

function page<T>(content: T[], number: number, totalPages: number) {
  return { content, number, totalPages, totalElements: totalPages * content.length, size: CSV_EXPORT_PAGE_SIZE };
}

afterEach(() => vi.restoreAllMocks());

describe("admin CSV exports", () => {
  it("fetches every page with a bounded size and keeps audit filters on every request", async () => {
    const filters = { search: "account", resourceType: "PLATFORM_ACCOUNT", labelActions: ["account.suspended"] };
    const events: AuditEvent[] = [
      { id: "1", actorEmail: "admin@mednet.lr", action: "account.suspended", resourceType: "PLATFORM_ACCOUNT", resourceId: "a", createdAt: "2026-10-01T00:00:00Z" },
      { id: "2", actorEmail: "admin@mednet.lr", action: "account.suspended", resourceType: "PLATFORM_ACCOUNT", resourceId: "b", createdAt: "2026-10-02T00:00:00Z" },
      { id: "3", actorEmail: "admin@mednet.lr", action: "account.suspended", resourceType: "PLATFORM_ACCOUNT", resourceId: "c", createdAt: "2026-10-03T00:00:00Z" },
    ];
    const auditPage = vi.spyOn(adminApi, "auditPage").mockImplementation(async (requestedPage) => {
      const contents = [events.slice(0, 2), events.slice(2)];
      return page(contents[requestedPage] ?? [], requestedPage, 2);
    });

    const exported = await exportAllAuditEvents(filters);

    expect(exported).toEqual(events);
    expect(auditPage).toHaveBeenNthCalledWith(1, 0, CSV_EXPORT_PAGE_SIZE, filters);
    expect(auditPage).toHaveBeenNthCalledWith(2, 1, CSV_EXPORT_PAGE_SIZE, filters);
    expect(auditPage).toHaveBeenCalledTimes(2);
  });

  it("keeps the selected request type and status filters across all exported pages", async () => {
    const requestsPage = vi.spyOn(adminApi, "serviceRequestsPage").mockImplementation(async (type, statuses, requestedPage) => {
      return page([{ id: `${type}-${statuses.join("-")}-${requestedPage}`, requestType: type, status: "OPEN", createdAt: "2026-10-01T00:00:00Z" }], requestedPage, 2);
    });

    const rows = await exportAllPatientRequests("HOME_CARE", ["OPEN", "IN_PROGRESS"]);

    expect(rows.map((row) => row.id)).toEqual(["HOME_CARE-OPEN-IN_PROGRESS-0", "HOME_CARE-OPEN-IN_PROGRESS-1"]);
    expect(requestsPage).toHaveBeenNthCalledWith(1, "HOME_CARE", ["OPEN", "IN_PROGRESS"], 0, CSV_EXPORT_PAGE_SIZE);
    expect(requestsPage).toHaveBeenNthCalledWith(2, "HOME_CARE", ["OPEN", "IN_PROGRESS"], 1, CSV_EXPORT_PAGE_SIZE);
  });

  it("quotes RFC4180 cells and protects formula-leading values after whitespace or controls", () => {
    expect(csvCell('Report, "quoted"\r\nvalue')).toBe('"Report, ""quoted""\r\nvalue"');
    expect(csvCell("\t =1+1")).toBe('"\'\t =1+1"');
    expect(csvCell("\u0001@SUM(A1:A2)")).toBe(`"'\u0001@SUM(A1:A2)"`);
    expect(csvCell("- ordinary text")).toBe('"\'- ordinary text"');
    expect(csvCell("ordinary text")).toBe('"ordinary text"');
    expect(toCsv(["Name"], [["a,b"], ["=1"]])).toBe('"Name"\r\n"a,b"\r\n"\'=1"');
  });

  it("does not issue extra requests when the list has no pages", async () => {
    const fetchPage = vi.fn().mockResolvedValue(page([], 0, 0));
    await expect(fetchAllPages(fetchPage)).resolves.toEqual([]);
    expect(fetchPage).toHaveBeenCalledTimes(1);
  });

  it("reports failed exports and allows retrying", async () => {
    const loadRows = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce([]);
    const download = vi.spyOn(csvExport, "downloadCsv").mockImplementation(() => undefined);
    render(<CsvExportButton filename="mednet-test" headers={["Reference"]} loadRows={loadRows} />);

    fireEvent.click(screen.getByRole("button", { name: "Export CSV" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("The CSV export could not be completed");

    fireEvent.click(screen.getByRole("button", { name: "Export CSV" }));
    await waitFor(() => expect(download).toHaveBeenCalledOnce());
    expect(loadRows).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
