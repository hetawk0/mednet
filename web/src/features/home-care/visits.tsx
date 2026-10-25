"use client";

import { Suspense, useState, type FormEvent } from "react";
import { StatusTimeline } from "@/components/healthcare/status-timeline";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DataTable, type DataColumn } from "@/components/ui/data-table";
import { ConfirmDialog } from "@/components/ui/dialog";
import { SearchInput, SelectField, TextInput } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { Pagination } from "@/components/ui/pagination";
import { ApiRequestError } from "@/lib/api";
import { ErrorRow } from "@/features/workspace/states";
import { usePartnerQueuePage, usePartnerQueueQuery } from "@/features/workspace/partner-queue";
import { actionError } from "@/features/workspace/use-resource";
import {
  ListCard,
  NoRecordSelected,
  RecordLayout,
  SelectedRecord,
  formatOperationsDateTime,
} from "@/features/workspace/record-layout";
import { homeCareApi, type HomeCareRequest } from "./home-care-api";
import { useHomeCare } from "./home-care-workspace";
import {
  homeCareBadge,
  homeCareReference,
  homeCareStage,
  homeCareTimeline,
  sortForQueue,
  toVisitInput,
  visitTimeToIso,
} from "./status";

type Notice = { tone: "success" | "critical"; text: string };

/**
 * Assigned home-care visits (layout follows the laboratory order queue: list plus "Selected record").
 * Columns and actions are limited to `PartnerRequestDetails` and the partner endpoints: no patient name
 * or contact, no reassignment, no rescheduling (the API sets a visit time once), no visit notes.
 */
export function VisitQueue({ limit, title = "Assigned home-care visits" }: { limit?: number; title?: string }) {
  return (
    <Suspense fallback={<p role="status" className="text-data text-text-secondary">Loading assigned visits…</p>}>
      <PagedVisitQueue limit={limit} title={title} />
    </Suspense>
  );
}

function PagedVisitQueue({ limit, title }: { limit?: number; title: string }) {
  const path = limit ? "/home-care" : "/home-care/visits";
  const { query, navigate } = usePartnerQueueQuery(path);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const pageSize = limit ?? 25;
  const requestKey = JSON.stringify([query.status, query.search, query.page, pageSize]);
  const { resource, reload } = usePartnerQueuePage(
    (signal) => homeCareApi.requestsPage({
      page: query.page - 1,
      size: pageSize,
      status: query.status || undefined,
      search: query.search.trim() || undefined,
    }, signal),
    requestKey,
  );

  const all = resource.state === "ready" ? sortForQueue(resource.data.content) : [];
  const rows = all;
  const selected = all.find((request) => request.id === selectedId) ?? null;
  const toSchedule = all.filter((request) => homeCareStage(request) === "unscheduled").length;
  const filtered = Boolean(query.search || query.status);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const search = formData.get("search");
    navigate({ ...query, search: typeof search === "string" ? search.trim() : "", page: 1 });
  }

  const columns: DataColumn<HomeCareRequest>[] = [
    {
      id: "request",
      header: "Request",
      primary: true,
      cell: (request) => <code className="font-mono font-semibold">{homeCareReference(request.id)}</code>,
    },
    { id: "care", header: "Care requested", cell: (request) => <span className="[overflow-wrap:anywhere]">{request.requestedService}</span> },
    {
      id: "visit",
      header: "Visit",
      cell: (request) => (request.scheduledAt ? formatOperationsDateTime(request.scheduledAt) : <span className="text-text-secondary">Not set</span>),
    },
    {
      id: "status",
      header: "Status",
      cell: (request) => (
        <Badge tone={homeCareBadge(request).tone} className="whitespace-nowrap">
          {homeCareBadge(request).label}
        </Badge>
      ),
    },
    {
      id: "open",
      header: "Details",
      align: "end",
      cell: (request) => (
        <Button
          size="sm"
          className="whitespace-nowrap"
          variant={request.id === selectedId ? "primary" : "secondary"}
          aria-pressed={request.id === selectedId}
          aria-label={`Open ${homeCareReference(request.id)}`}
          onClick={() => setSelectedId(request.id)}
        >
          Open
        </Button>
      ),
    },
  ];

  return (
    <>
      {notice ? (
        <Alert
          tone={notice.tone}
          live={notice.tone === "critical" ? "assertive" : "polite"}
          title={notice.text}
          action={
            <Button size="sm" variant="tertiary" onClick={() => setNotice(null)}>
              Dismiss
            </Button>
          }
        />
      ) : null}
      <RecordLayout
        list={
          <ListCard
            eyebrow="Assigned work"
            title={title}
            action={toSchedule ? <Badge tone="warning">{`${toSchedule} on this page need a visit time`}</Badge> : undefined}
          >
            <form onSubmit={submitSearch} role="search" aria-label="Visit filters" className="grid gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto] md:items-end">
              <SearchInput
                label="Search visits"
                placeholder="Care or location"
                maxLength={120}
                key={query.search}
                name="search"
                defaultValue={query.search}
              />
              <SelectField
                label="Visit status"
                hideLabel
                value={query.status}
                onChange={(event) => navigate({ ...query, status: event.target.value, page: 1 })}
                options={[
                  { value: "", label: "Status: all" },
                  { value: "OPEN", label: "Open · scheduled or not" },
                  { value: "IN_PROGRESS", label: "Visit in progress" },
                  { value: "RESOLVED", label: "Completed" },
                  { value: "CANCELLED", label: "Cancelled" },
                ]}
              />
              <Button type="submit" variant="secondary">Search</Button>
            </form>
            {resource.state === "error" ? (
              <ErrorRow title="Your assigned visits could not be loaded" status={resource.status} onRetry={reload} />
            ) : (
              <DataTable
                caption={title}
                columns={columns}
                rows={rows}
                rowKey={(request) => request.id}
                selectedKey={selectedId ?? undefined}
                loading={resource.state === "loading"}
                empty={{
                  title: filtered ? "No visits match these filters" : "No home-care requests are assigned to you",
                  description: filtered ? "Try different search words or clear the status filter." : "A MedNet administrator assigns patients' home-care requests to your account.",
                }}
              />
            )}
            {!limit && resource.state === "ready" ? (
              <Pagination
                page={resource.data.number + 1}
                totalPages={resource.data.totalPages}
                onPageChange={(next) => navigate({ ...query, page: next })}
                label="Visit queue pages"
              />
            ) : null}
            {limit && resource.state === "ready" && resource.data.totalElements > limit ? (
              <p className="text-caption text-text-secondary">{`Showing ${limit} of ${resource.data.totalElements}. All visits are under Visits.`}</p>
            ) : null}
          </ListCard>
        }
        record={
          selected ? (
            <VisitDetail key={selected.id} request={selected} onNotice={setNotice} onQueueChange={reload} />
          ) : (
            <NoRecordSelected>Open a request to see the care requested, where, and what you can do next.</NoRecordSelected>
          )
        }
      />
    </>
  );
}

function VisitDetail({
  request,
  onNotice,
  onQueueChange,
}: {
  request: HomeCareRequest;
  onNotice: (notice: Notice) => void;
  onQueueChange: () => void;
}) {
  const { replaceRequest, reloadRequests } = useHomeCare();
  const [confirming, setConfirming] = useState<"start" | "complete" | null>(null);
  const [busy, setBusy] = useState(false);
  const stage = homeCareStage(request);
  const reference = homeCareReference(request.id);

  async function changeStatus(next: "start" | "complete") {
    setBusy(true);
    try {
      const saved = next === "start" ? await homeCareApi.startVisit(request.id) : await homeCareApi.completeVisit(request.id);
      replaceRequest(saved);
      onQueueChange();
      onNotice({
        tone: "success",
        text: next === "start" ? `${reference}: visit started. The patient was notified.` : `${reference}: visit completed. The patient was notified.`,
      });
    } catch (error) {
      onNotice({ tone: "critical", text: actionError(error, "The visit could not be updated.") });
      // The request may have changed elsewhere (cancelled by the patient or an administrator).
      if (error instanceof ApiRequestError && (error.status === 409 || error.status === 404)) {
        reloadRequests();
        onQueueChange();
      }
    } finally {
      setBusy(false);
      setConfirming(null);
    }
  }

  return (
    <>
      <SelectedRecord
        focusKey={request.id}
        reference={reference}
        title={<Badge tone={homeCareBadge(request).tone}>{homeCareBadge(request).label}</Badge>}
        actions={
          stage === "scheduled" ? (
            <Button onClick={() => setConfirming("start")}>Start visit</Button>
          ) : stage === "in-progress" ? (
            <Button onClick={() => setConfirming("complete")}>Mark visit completed</Button>
          ) : null
        }
        restricted="Only the care requested and the visit location are shared with you. Patient name, contact details and medical records are not available here."
      >
        <div className="grid gap-3 md:grid-cols-2">
          <div className="rounded-sm bg-green-50 p-4">
            <p className="text-caption text-text-secondary">Care requested (as written by the patient)</p>
            <p className="mt-1 font-semibold text-ink [overflow-wrap:anywhere]">{request.requestedService}</p>
          </div>
          <div className="rounded-sm bg-green-50 p-4">
            <p className="text-caption text-text-secondary">Visit location (as written by the patient)</p>
            <p className="mt-1 font-semibold text-ink [overflow-wrap:anywhere]">{request.locationDescription ?? "Not provided"}</p>
          </div>
        </div>
        <DescriptionList
          columns={2}
          items={[
            { term: "Visit time", detail: request.scheduledAt ? `${formatOperationsDateTime(request.scheduledAt)} (Liberia time)` : "Not set" },
            { term: "Requested", detail: formatOperationsDateTime(request.createdAt) },
            { term: "Last updated", detail: formatOperationsDateTime(request.updatedAt) },
          ]}
        />
        <StatusTimeline label={`Progress of ${reference}`} steps={homeCareTimeline(request, formatOperationsDateTime)} />
        {stage === "unscheduled" ? (
          <ScheduleForm request={request} onNotice={onNotice} onQueueChange={onQueueChange} />
        ) : stage === "scheduled" ? (
          <p className="text-caption text-text-secondary">
            Start the visit when you arrive. The visit time cannot be changed in MedNet once set; if it must move, contact a MedNet administrator.
          </p>
        ) : stage === "in-progress" ? (
          <p className="text-caption text-text-secondary">Mark the visit completed when the requested care has been given. The patient is told.</p>
        ) : stage === "completed" ? (
          <p className="text-caption text-text-secondary">This visit is complete. No further action is available.</p>
        ) : stage === "cancelled" ? (
          <p className="text-caption text-text-secondary">This request was cancelled by the patient or an administrator. Do not visit.</p>
        ) : null}
      </SelectedRecord>
      <ConfirmDialog
        open={confirming !== null}
        onClose={() => setConfirming(null)}
        onConfirm={() => confirming && void changeStatus(confirming)}
        title={confirming === "complete" ? `Mark ${reference} completed?` : `Start the visit for ${reference}?`}
        description={
          confirming === "complete"
            ? "The request is closed as completed and the patient is notified. This cannot be undone from the home-care workspace."
            : "The request moves to in progress and the patient is notified. This cannot be undone from the home-care workspace."
        }
        confirmLabel={confirming === "complete" ? "Mark completed" : "Start visit"}
        loading={busy}
      />
    </>
  );
}

/** Sets the visit time once (`PATCH …/schedule`). The API requires a future time. */
function ScheduleForm({
  request,
  onNotice,
  onQueueChange,
}: {
  request: HomeCareRequest;
  onNotice: (notice: Notice) => void;
  onQueueChange: () => void;
}) {
  const { replaceRequest, reloadRequests } = useHomeCare();
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  const [failure, setFailure] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  // Earliest selectable time, fixed when the form opens; the API checks again on submit.
  const [minimum] = useState(() => toVisitInput(new Date(Date.now() + 60_000)));
  const reference = homeCareReference(request.id);

  function review(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFailure("");
    const iso = visitTimeToIso(value);
    const problem = !iso ? "Choose the visit date and time." : new Date(iso).getTime() <= Date.now() ? "Choose a time in the future." : "";
    if (problem) {
      setError(problem);
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setError("");
    setConfirming(true);
  }

  async function submit() {
    const iso = visitTimeToIso(value);
    if (!iso) return;
    setBusy(true);
    try {
      const saved = await homeCareApi.schedule(request.id, iso);
      replaceRequest(saved);
      onQueueChange();
      onNotice({ tone: "success", text: `${reference}: visit scheduled for ${formatOperationsDateTime(saved.scheduledAt ?? iso)}. The patient was notified.` });
    } catch (caught) {
      const message = actionError(caught, "The visit could not be scheduled.");
      if (caught instanceof ApiRequestError && (caught.status === 409 || caught.status === 404)) {
        // Changed elsewhere (cancelled or already scheduled): this form goes away with the refresh, so
        // the explanation is shown above the queue instead.
        onNotice({ tone: "critical", text: message });
        reloadRequests();
        onQueueChange();
      } else {
        setFailure(message);
      }
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  }

  return (
    <form noValidate onSubmit={review} className="flex flex-col gap-3 border-t border-border pt-5">
      <TextInput
        label="Visit date and time"
        type="datetime-local"
        required
        min={minimum}
        description="Liberia time (GMT). It can be set once; the patient sees it."
        value={value}
        error={error || undefined}
        onChange={(event) => setValue(event.target.value)}
      />
      {failure ? <Alert tone="critical" live="assertive" title={failure} /> : null}
      <div>
        <Button type="submit">Schedule visit</Button>
      </div>
      <ConfirmDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => void submit()}
        title={`Schedule the visit for ${reference}?`}
        description={`${value ? formatOperationsDateTime(visitTimeToIso(value) ?? new Date().toISOString()) : ""} (Liberia time). The patient is notified, and the time cannot be changed in MedNet afterwards.`}
        confirmLabel="Schedule visit"
        loading={busy}
      />
    </form>
  );
}
