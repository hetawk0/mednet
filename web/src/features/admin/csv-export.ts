import type { Page } from "@/features/find-care/provider-api";
import { adminApi, type AdminAppointment, type AuditEvent, type OperationalRequest, type PatientServiceRequestSummary } from "./admin-api";

export const CSV_EXPORT_PAGE_SIZE = 100;

export type AuditExportFilters = {
  search: string;
  resourceType: string;
  labelActions: string[];
};

export async function fetchAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<Page<T>>,
  pageSize = CSV_EXPORT_PAGE_SIZE,
): Promise<T[]> {
  const first = await fetchPage(0, pageSize);
  if (!Number.isSafeInteger(first.totalPages) || first.totalPages < 0) {
    throw new Error("The API returned invalid pagination metadata.");
  }

  const records = [...first.content];
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await fetchPage(page, pageSize);
    records.push(...next.content);
  }
  return records;
}

export function exportAllAuditEvents(filters: AuditExportFilters): Promise<AuditEvent[]> {
  return fetchAllPages((page, size) => adminApi.auditPage(page, size, filters));
}

export function exportAllAppointments(): Promise<AdminAppointment[]> {
  return fetchAllPages((page, size) => adminApi.appointments(page, size));
}

export function exportAllPatientRequests(
  type: "HOME_CARE" | "LABORATORY",
  statuses: string[],
): Promise<PatientServiceRequestSummary[]> {
  return fetchAllPages((page, size) => adminApi.serviceRequestsPage(type, statuses, page, size));
}

export function exportAllOperationalReferences(): Promise<OperationalRequest[]> {
  return fetchAllPages((page, size) => adminApi.requestsPage(page, size));
}

export function csvCell(value: unknown): string {
  const text = value === null || value === undefined ? "" : String(value);
  const protectedText = /^[\s\u0000-\u001f\u007f-\u009f]*[=+\-@]/u.test(text) ? `'${text}` : text;
  return `"${protectedText.replaceAll('"', '""')}"`;
}

export function toCsv(headers: string[], rows: unknown[][]): string {
  return [headers, ...rows].map((row) => row.map(csvCell).join(",")).join("\r\n");
}

export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
