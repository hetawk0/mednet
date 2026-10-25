"use client";

import { useState } from "react";
import { AppointmentStatusBadge } from "@/components/healthcare/status";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { DataTable, type DataColumn } from "@/components/ui/data-table";
import { SearchInput, SelectField } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { MetricCard, MetricGrid } from "@/components/ui/metric-card";
import { Pagination } from "@/components/ui/pagination";
import { StateMessage } from "@/components/ui/state-message";
import { consultationModeLabel } from "@/features/find-care/format";
import { useDebouncedValue, usePageFor } from "@/features/workspace/paging";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { useResource } from "@/features/workspace/use-resource";
import { adminApi, type AdminAppointment, type AdminOverview, type AuditEvent } from "./admin-api";
import { CsvExportButton } from "./csv-export-button";
import { exportAllAppointments, exportAllAuditEvents } from "./csv-export";
import { formatAdminDateTime } from "./frame";
import { MODULE_LABELS, auditActionLabel, auditActionsMatchingLabel, moduleStatus, shortReference } from "./permissions";

/**
 * `GET /admin/appointments`: identifiers, status, mode and times only. The API returns no names,
 * reasons or notes, so administrators coordinate without clinical context.
 */
export function AdminAppointments() {
  const [page, setPage] = useState(0);
  const size = 25;
  const { resource, reload } = useResource((signal) => adminApi.appointments(page, size, signal), String(page));

  const columns: DataColumn<AdminAppointment>[] = [
    { id: "ref", header: "Reference", primary: true, cell: (row) => <code className="font-mono">APT-{shortReference(row.id)}</code> },
    { id: "when", header: "Starts (Liberia time)", cell: (row) => formatAdminDateTime(row.startsAt) },
    { id: "mode", header: "Mode", cell: (row) => consultationModeLabel(row.consultationMode) },
    { id: "status", header: "Status", cell: (row) => <AppointmentStatusBadge status={row.status} /> },
    { id: "patient", header: "Patient ref.", cell: (row) => <code className="font-mono">{shortReference(row.patientAccountId)}</code> },
    { id: "provider", header: "Provider ref.", cell: (row) => <code className="font-mono">{shortReference(row.providerApplicationId)}</code> },
    { id: "requested", header: "Requested", cell: (row) => formatAdminDateTime(row.createdAt) },
  ];

  return (
    <Card className="flex flex-col gap-5">
      <CardHeader eyebrow="Appointments and services" title="All appointments" />
      <p className="text-caption text-text-secondary">
        Newest requests first. References are shortened internal identifiers; names, reasons and consultation content are not
        available to administrators. Opening this list is recorded in the audit trail.
      </p>
      <div className="flex justify-end">
        <CsvExportButton
          filename="mednet-appointments"
          headers={["Reference", "Starts (Liberia time)", "Mode", "Status", "Patient ref.", "Provider ref.", "Requested"]}
          disabled={resource.state !== "ready"}
          loadRows={async () =>
            (await exportAllAppointments()).map((row) => [
              `APT-${shortReference(row.id)}`,
              formatAdminDateTime(row.startsAt),
              consultationModeLabel(row.consultationMode),
              row.status,
              shortReference(row.patientAccountId),
              shortReference(row.providerApplicationId),
              formatAdminDateTime(row.createdAt),
            ])
          }
        />
      </div>
      {resource.state === "error" ? (
        <ErrorRow title="Appointments could not be loaded" status={resource.status} onRetry={reload} />
      ) : (
        <DataTable
          caption="Appointments"
          columns={columns}
          rows={resource.state === "ready" ? resource.data.content : []}
          rowKey={(row) => row.id}
          loading={resource.state === "loading"}
          empty={{ title: "No appointments yet" }}
        />
      )}
      {resource.state === "ready" ? (
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label="Appointment pages" />
          <p className="text-caption text-text-secondary">{resource.data.totalElements} appointments</p>
        </div>
      ) : null}
    </Card>
  );
}

/**
 * `GET /admin/audit?page=` (Phase 5D): the whole administrative audit trail, newest first, paged and
 * filtered on the server. Before 5D only the latest 100 events were loaded and filtered in the browser.
 */
export const AUDIT_PAGE_SIZE = 25;

export function AuditTrail({ title = "Audit trail" }: { title?: string }) {
  const [query, setQuery] = useState("");
  const [resourceType, setResourceType] = useState("");
  const search = useDebouncedValue(query.trim());
  const [page, setPage] = usePageFor(JSON.stringify([search, resourceType]));
  const { resource, reload } = useResource(
    (signal) => adminApi.auditPage(page, AUDIT_PAGE_SIZE, { search, resourceType, labelActions: auditActionsMatchingLabel(search) }, signal),
    JSON.stringify([page, search, resourceType]),
  );
  const types = useResource((signal) => adminApi.auditResourceTypes(signal), "audit-types");

  const events = resource.state === "ready" ? resource.data.content : [];
  const filtering = Boolean(search || resourceType);

  const columns: DataColumn<AuditEvent>[] = [
    { id: "when", header: "When (Liberia time)", cell: (event) => <time dateTime={event.createdAt}>{formatAdminDateTime(event.createdAt)}</time> },
    { id: "action", header: "Action", primary: true, cell: (event) => auditActionLabel(event.action) },
    { id: "actor", header: "By", cell: (event) => <span className="[overflow-wrap:anywhere]">{event.actorEmail}</span> },
    { id: "resource", header: "Record", cell: (event) => `${event.resourceType.replaceAll("_", " ")} ${shortReference(event.resourceId)}` },
  ];

  return (
    <Card className="flex flex-col gap-5">
      <CardHeader eyebrow="Security" title={title} />
      <div className="flex justify-end">
        <CsvExportButton
          filename="mednet-audit-trail"
          headers={["When (Liberia time)", "Action", "By", "Record"]}
          disabled={resource.state !== "ready"}
          loadRows={async () =>
            (await exportAllAuditEvents({
              search,
              resourceType,
              labelActions: auditActionsMatchingLabel(search),
            })).map((event) => [
              formatAdminDateTime(event.createdAt),
              auditActionLabel(event.action),
              event.actorEmail,
              `${event.resourceType.replaceAll("_", " ")} ${shortReference(event.resourceId)}`,
            ])
          }
        />
      </div>
      <div role="search" aria-label="Audit filters" className="grid gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <SearchInput label="Search the audit trail" placeholder="Action, person or record" value={query} onChange={(event) => setQuery(event.target.value)} />
        <SelectField
          label="Record type"
          hideLabel
          value={resourceType}
          onChange={(event) => setResourceType(event.target.value)}
          options={[
            { value: "", label: "Record: all" },
            ...(types.resource.state === "ready" ? types.resource.data : []).map((type) => ({ value: type, label: type.replaceAll("_", " ") })),
          ]}
        />
      </div>
      {resource.state === "loading" ? (
        <LoadingRows label="Loading the audit trail" />
      ) : resource.state === "error" ? (
        <ErrorRow title="The audit trail could not be loaded" status={resource.status} onRetry={reload} />
      ) : events.length === 0 ? (
        <StateMessage kind="empty" title={filtering ? "No events match" : "No audit events yet"} />
      ) : (
        <DataTable caption={title} columns={columns} rows={events} rowKey={(event) => event.id} />
      )}
      {resource.state === "ready" && resource.data.totalElements > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label="Audit trail pages" />
          <p className="text-caption text-text-secondary">{`${resource.data.totalElements} ${filtering ? "matching events" : "events"}`}</p>
        </div>
      ) : null}
      <p className="text-caption text-text-secondary">
        The trail is append-only. Records are identified by shortened internal references; no clinical content is stored in it.
      </p>
    </Card>
  );
}

/** Module availability from `GET /admin/overview` (read-only). */
export function ModuleStatusList({ overview }: { overview: AdminOverview }) {
  return (
    <ul className="divide-y divide-line-soft">
      {overview.modules.map((module) => {
        const status = moduleStatus(module.status);
        return (
          <li key={module.key} className="flex min-h-12 items-center justify-between gap-3 py-2.5">
            <span className="text-data text-ink">{MODULE_LABELS[module.key] ?? module.key}</span>
            <Badge tone={status.tone}>{status.label}</Badge>
          </li>
        );
      })}
    </ul>
  );
}

function uptime(seconds: number): string {
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return days ? `${days} d ${hours} h` : hours ? `${hours} h ${minutes} min` : `${minutes} min`;
}

/** Shared loader for views built on the overview endpoint. */
export function useOverview() {
  return useResource((signal) => adminApi.overview(signal), "overview");
}

/** Configuration (super administrator): read-only platform status. Nothing here is editable in the API. */
export function ConfigurationView() {
  const { resource, reload } = useOverview();
  if (resource.state === "loading") return <LoadingRows label="Loading platform status" />;
  if (resource.state === "error") return <ErrorRow title="Platform status could not be loaded" status={resource.status} onRetry={reload} />;
  const overview = resource.data;
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <Card>
        <CardHeader eyebrow="Platform status" title="Modules" />
        <p className="mt-1.5 text-data text-text-secondary">
          What the API reports for each area. Database-backed areas are available when PostgreSQL is configured.
        </p>
        <div className="mt-4">
          <ModuleStatusList overview={overview} />
        </div>
      </Card>
      <div className="flex flex-col gap-6">
        <Card>
          <CardHeader eyebrow="Service" title="API health" titleAs="h3" />
          <DescriptionList
            className="mt-4"
            items={[
              { term: "Status", detail: overview.apiStatus === "UP" ? "Running" : overview.apiStatus },
              { term: "Running for", detail: uptime(overview.uptimeSeconds) },
              { term: "Checked", detail: formatAdminDateTime(overview.checkedAt) },
            ]}
          />
        </Card>
        <Card>
          <CardHeader eyebrow="Settings" title="How configuration changes" titleAs="h3" />
          <p className="mt-1.5 text-data text-text-secondary">
            Platform settings, integrations and the configured administrator are managed through deployment environment
            settings, not in this workspace. Changes there are made by the operations team and are not shown here.
          </p>
        </Card>
      </div>
    </div>
  );
}

/** Reports (administrator): live counts from the overview plus the recent audit trail. No export. */
export function ReportsView() {
  const { resource, reload } = useOverview();
  return (
    <div className="flex flex-col gap-6">
      {resource.state === "loading" ? (
        <LoadingRows label="Loading operational counts" />
      ) : resource.state === "error" ? (
        <ErrorRow title="Operational counts could not be loaded" status={resource.status} onRetry={reload} />
      ) : resource.data.counts ? (
        <MetricGrid>
          <MetricCard tone="warning" value={resource.data.counts.pendingProviders} label="Provider applications" hint="Awaiting review" />
          <MetricCard tone="success" value={resource.data.counts.activeAccounts} label="Active accounts" hint="All account types" />
          <MetricCard tone="info" value={resource.data.counts.openHomeCareRequests} label="Home-care requests" hint="Open or in progress" />
          <MetricCard tone="info" value={resource.data.counts.openLaboratoryRequests} label="Laboratory requests" hint="Open or in progress" />
        </MetricGrid>
      ) : (
        <StateMessage kind="empty" title="Counts are unavailable" description="The API reports that its database is not configured." />
      )}
      <AuditTrail title="Recent administrative activity" />
    </div>
  );
}
