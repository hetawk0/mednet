"use client";

import { Suspense, useState, type FormEvent } from "react";
import { StatusTimeline } from "@/components/healthcare/status-timeline";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { DataTable, type DataColumn } from "@/components/ui/data-table";
import { ConfirmDialog } from "@/components/ui/dialog";
import { SearchInput, SelectField, TextArea } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { Pagination } from "@/components/ui/pagination";
import { ApiRequestError } from "@/lib/api";
import { ErrorRow } from "@/features/workspace/states";
import { actionError } from "@/features/workspace/use-resource";
import { usePartnerQueuePage, usePartnerQueueQuery } from "@/features/workspace/partner-queue";
import {
  ListCard,
  NoRecordSelected,
  RecordLayout,
  SelectedRecord,
  formatOperationsDateTime,
} from "@/features/workspace/record-layout";
import { LAB_LIMITS, labApi, type LabRequest, type SubmittedResult } from "./lab-api";
import { useLaboratory } from "./laboratory-workspace";
import { LAB_REQUEST_STATUS, labReference, orderBadge, requestTimeline } from "./status";

/**
 * Laboratory order queue (Figma "Assigned work · Laboratory order queue" + "Order detail"). Columns
 * and actions are limited to what `PartnerRequestDetails` carries: no patient reference, requesting
 * doctor, urgency or turnaround (none exist in the API), and no accept/reject (the API has neither).
 */
export function OrderQueue({
  defaultStatus = "",
  limit,
  title = "Laboratory order queue",
}: {
  defaultStatus?: string;
  limit?: number;
  title?: string;
}) {
  return (
    <Suspense fallback={<p role="status" className="text-data text-text-secondary">Loading laboratory orders…</p>}>
      <PagedOrderQueue defaultStatus={defaultStatus} limit={limit} title={title} />
    </Suspense>
  );
}

function PagedOrderQueue({
  defaultStatus,
  limit,
  title,
}: {
  defaultStatus: string;
  limit?: number;
  title: string;
}) {
  const { latestResult } = useLaboratory();
  const path = limit ? "/laboratory" : "/laboratory/orders";
  const { query, navigate } = usePartnerQueueQuery(path);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ tone: "success" | "critical"; text: string } | null>(null);
  const pageSize = limit ?? 25;
  const requestKey = JSON.stringify([query.status || defaultStatus, query.search, query.page, pageSize]);
  const { resource, reload } = usePartnerQueuePage(
    (signal) => labApi.requestsPage({
      page: query.page - 1,
      size: pageSize,
      status: query.status || defaultStatus || undefined,
      search: query.search.trim() || undefined,
    }, signal),
    requestKey,
  );

  const all = resource.state === "ready" ? resource.data.content : [];
  const rows = all;
  const selected = all.find((request) => request.id === selectedId) ?? null;
  const newCount = all.filter((request) => request.status === "OPEN").length;
  const filtered = Boolean(query.search || query.status || defaultStatus);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const search = formData.get("search");
    navigate({ ...query, search: typeof search === "string" ? search.trim() : "", page: 1 });
  }

  const columns: DataColumn<LabRequest>[] = [
    {
      id: "order",
      header: "Order",
      primary: true,
      cell: (request) => <code className="font-mono font-semibold">{labReference(request.id)}</code>,
    },
    { id: "test", header: "Requested test", cell: (request) => <span className="[overflow-wrap:anywhere]">{request.requestedService}</span> },
    {
      id: "status",
      header: "Status",
      cell: (request) => (
        <Badge tone={orderBadge(request, latestResult(request.id) ?? request.latestResult).tone} className="whitespace-nowrap">
          {orderBadge(request, latestResult(request.id) ?? request.latestResult).label}
        </Badge>
      ),
    },
    { id: "received", header: "Received", cell: (request) => formatOperationsDateTime(request.createdAt) },
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
          aria-label={`Open ${labReference(request.id)}`}
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
            action={newCount ? <Badge tone="warning">{`${newCount} on this page not started`}</Badge> : undefined}
          >
            <form onSubmit={submitSearch} role="search" aria-label="Order filters" className="grid gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto] md:items-end">
              <SearchInput
                label="Search orders"
                placeholder="Test or location"
                maxLength={120}
                key={query.search}
                name="search"
                defaultValue={query.search}
              />
              <SelectField
                label="Order status"
                hideLabel
                value={query.status || defaultStatus}
                onChange={(event) => navigate({ ...query, status: event.target.value, page: 1 })}
                options={[{ value: "", label: "Status: all" }, ...Object.entries(LAB_REQUEST_STATUS).map(([value, { label }]) => ({ value, label }))]}
              />
              <Button type="submit" variant="secondary">Search</Button>
            </form>
            {resource.state === "error" ? (
              <ErrorRow title="Your assigned orders could not be loaded" status={resource.status} onRetry={reload} />
            ) : (
              <DataTable
                caption={title}
                columns={columns}
                rows={rows}
                rowKey={(request) => request.id}
                selectedKey={selectedId ?? undefined}
                loading={resource.state === "loading"}
                empty={{
                  title: filtered ? "No orders match these filters" : "No orders are assigned to you",
                  description: filtered ? "Try different search words or clear the status filter." : "A MedNet administrator assigns laboratory requests to your account.",
                }}
              />
            )}
            {!limit && resource.state === "ready" ? (
              <Pagination
                page={resource.data.number + 1}
                totalPages={resource.data.totalPages}
                onPageChange={(next) => navigate({ ...query, page: next })}
                label="Order queue pages"
              />
            ) : null}
            {limit && resource.state === "ready" && resource.data.totalElements > limit ? (
              <p className="text-caption text-text-secondary">{`Showing ${limit} of ${resource.data.totalElements}. All orders are under Orders.`}</p>
            ) : null}
          </ListCard>
        }
        record={
          selected ? (
            <OrderDetail
              key={selected.id}
              request={selected}
              onNotice={setNotice}
              onQueueChange={reload}
            />
          ) : (
            <NoRecordSelected>Open an order to see the requested test and what you can do next.</NoRecordSelected>
          )
        }
      />
    </>
  );
}

function OrderDetail({
  request,
  onNotice,
  onQueueChange,
}: {
  request: LabRequest;
  onNotice: (notice: { tone: "success" | "critical"; text: string }) => void;
  onQueueChange: () => void;
}) {
  const { replaceRequest, reloadRequests, latestResult, addResult } = useLaboratory();
  const [confirmStart, setConfirmStart] = useState(false);
  const [starting, setStarting] = useState(false);
  const latest = latestResult(request.id) ?? request.latestResult;
  const pendingReview = latest?.status === "PENDING_REVIEW";
  const returned = latest?.status === "RETURNED";
  const reference = labReference(request.id);

  async function start() {
    setStarting(true);
    try {
      const saved = await labApi.startProcessing(request.id);
      replaceRequest(saved);
      onQueueChange();
      onNotice({ tone: "success", text: `${reference} is processing. The patient was notified.` });
    } catch (error) {
      onNotice({ tone: "critical", text: actionError(error, "The order could not be started.") });
      // The order may have changed elsewhere (cancelled by the patient or an administrator).
      if (error instanceof ApiRequestError && (error.status === 409 || error.status === 404)) {
        reloadRequests();
        onQueueChange();
      }
    } finally {
      setStarting(false);
      setConfirmStart(false);
    }
  }

  return (
    <>
      <SelectedRecord
        focusKey={request.id}
        reference={reference}
        title={
          <Badge tone={orderBadge(request, latest).tone}>{orderBadge(request, latest).label}</Badge>
        }
        actions={
          request.status === "OPEN" ? (
            <Button onClick={() => setConfirmStart(true)}>Start processing</Button>
          ) : null
        }
        restricted="Only the requested test is shared with the laboratory. Patient identity, records and other results are not available here."
      >
        <div className="rounded-sm bg-green-50 p-4">
          <p className="text-caption text-text-secondary">Requested test (as written by the patient)</p>
          <p className="mt-1 font-semibold text-ink [overflow-wrap:anywhere]">{request.requestedService}</p>
        </div>
        <DescriptionList
          columns={2}
          items={[
            { term: "Received", detail: formatOperationsDateTime(request.createdAt) },
            { term: "Last updated", detail: formatOperationsDateTime(request.updatedAt) },
          ]}
        />
        <StatusTimeline label={`Progress of ${reference}`} steps={requestTimeline(request, latest, formatOperationsDateTime)} />
        {request.status === "IN_PROGRESS" ? (
          pendingReview ? (
            <Alert tone="info" title="Result awaiting doctor review">
              A doctor caring for the patient releases or returns it. It is not visible to the patient until a doctor releases it.
            </Alert>
          ) : (
            <>
              {returned ? (
                <Alert tone="warning" title="A doctor returned your result">
                  {`Returned ${formatOperationsDateTime(latest?.reviewedAt ?? latest?.createdAt ?? request.updatedAt)}. It was not released to the patient. Submit a corrected result below.`}
                </Alert>
              ) : null}
              <ResultForm
                request={request}
                correction={returned}
                onSubmitted={(saved) => {
                  addResult(saved);
                  onQueueChange();
                  onNotice({ tone: "success", text: `Result for ${reference} submitted for doctor review. It is not released to the patient yet.` });
                }}
                onAlreadyPending={() => {
                  reloadRequests();
                  onQueueChange();
                  onNotice({ tone: "critical", text: "A result for this order is already awaiting doctor review." });
                }}
                onQueueChange={onQueueChange}
              />
            </>
          )
        ) : request.status === "RESOLVED" ? (
          <p className="text-caption text-text-secondary">A doctor released the result to the patient. The order is complete.</p>
        ) : request.status === "CANCELLED" ? (
          <p className="text-caption text-text-secondary">This order was cancelled. No result can be submitted.</p>
        ) : (
          <p className="text-caption text-text-secondary">Start processing to record a result. The patient is told when processing starts.</p>
        )}
      </SelectedRecord>
      <ConfirmDialog
        open={confirmStart}
        onClose={() => setConfirmStart(false)}
        onConfirm={() => void start()}
        title={`Start processing ${reference}?`}
        description="The order moves to processing and the patient is notified. This cannot be undone from the laboratory workspace."
        confirmLabel="Start processing"
        loading={starting}
      />
    </>
  );
}

/**
 * Result entry. The API takes a text summary (up to 9,700 characters) and stores it as PENDING_REVIEW:
 * it is never released by the laboratory. Corrections after a doctor returns a result are a new submission.
 */
function ResultForm({
  request,
  correction = false,
  onSubmitted,
  onAlreadyPending,
  onQueueChange,
}: {
  request: LabRequest;
  correction?: boolean;
  onSubmitted: (result: SubmittedResult) => void;
  onAlreadyPending: () => void;
  onQueueChange: () => void;
}) {
  const { reloadRequests } = useLaboratory();
  const [summary, setSummary] = useState("");
  const [error, setError] = useState("");
  const [failure, setFailure] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  function review(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFailure("");
    if (!summary.trim()) {
      setError("Enter the result.");
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setError("");
    setConfirming(true);
  }

  async function submit() {
    setBusy(true);
    try {
      const saved = await labApi.submitResult(request.id, summary.trim());
      setSummary("");
      onSubmitted(saved);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 409 && /awaiting provider review/i.test(caught.message)) {
        onAlreadyPending();
      } else {
        setFailure(actionError(caught, "The result could not be submitted."));
        if (caught instanceof ApiRequestError && (caught.status === 409 || caught.status === 404)) {
          reloadRequests();
          onQueueChange();
        }
      }
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  }

  return (
    <form noValidate onSubmit={review} className="flex flex-col gap-3 border-t border-border pt-5">
      <TextArea
        label={correction ? "Corrected result" : "Result"}
        description="Enter the result as it should appear to the reviewing doctor. It stays private until a doctor releases it."
        required
        rows={6}
        maxLength={LAB_LIMITS.resultSummary}
        value={summary}
        error={error || undefined}
        onChange={(event) => setSummary(event.target.value)}
      />
      <p className="text-caption text-text-secondary">{`${summary.length.toLocaleString("en-GB")} / ${LAB_LIMITS.resultSummary.toLocaleString("en-GB")} characters`}</p>
      {failure ? <Alert tone="critical" live="assertive" title={failure} /> : null}
      <div>
        <Button type="submit">Submit for doctor review</Button>
      </div>
      <ConfirmDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => void submit()}
        title="Submit this result for doctor review?"
        description="A doctor caring for the patient reviews it and decides whether to release it. You cannot edit it after submitting; if it is returned, submit a corrected result."
        confirmLabel="Submit result"
        loading={busy}
      />
    </form>
  );
}
