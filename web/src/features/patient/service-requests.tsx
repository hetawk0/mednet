"use client";

import { ClipboardList, House } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ServiceRequestStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput, SelectField, TextArea, TextInput } from "@/components/ui/inputs";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { formatDate, formatShortDate, formatWhen } from "./format";
import { LIMITS, patientApi, type ServiceRequest } from "./patient-api";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { useDebouncedValue, usePageFor } from "@/features/workspace/paging";
import { CountBadge } from "./view-frame";

type Kind = "lab" | "home-care";

const copy = {
  lab: {
    eyebrow: "Laboratory",
    title: "Laboratory requests",
    noun: "request",
    empty: "You have not requested a laboratory test.",
    formTitle: "Request a laboratory test",
    formHelp: "A MedNet laboratory partner reviews the request. Released results appear under Laboratory results.",
    icon: ClipboardList,
  },
  "home-care": {
    eyebrow: "Home care",
    title: "Home-care requests",
    noun: "request",
    empty: "You have not requested home care.",
    formTitle: "Request home care",
    formHelp: "A MedNet home-care partner reviews the request and may schedule a visit.",
    icon: House,
  },
} as const;

/**
 * Patient laboratory and home-care requests (`PatientServiceRequestController`). Only OPEN
 * requests can be cancelled; the API enforces this and returns 409 otherwise.
 */
export function ServiceRequestPanel({ kind }: { kind: Kind }) {
  const text = copy[kind];
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const search = useDebouncedValue(query.trim());
  const filterKey = JSON.stringify([kind, search, status]);
  const [page, setPage] = usePageFor(filterKey);
  const { resource, reload } = useResource(
    (signal) =>
      kind === "lab"
        ? patientApi.labRequestsPage(page, 25, { status, search }, signal)
        : patientApi.homeCareRequestsPage(page, 25, { status, search }, signal),
    JSON.stringify([filterKey, page]),
  );
  const [cancelling, setCancelling] = useState<ServiceRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "critical"; text: string } | null>(null);

  const requests = resource.state === "ready" ? resource.data.content : [];

  async function cancel() {
    if (!cancelling) return;
    setBusy(true);
    try {
      await (kind === "lab" ? patientApi.cancelLab(cancelling.id) : patientApi.cancelHomeCare(cancelling.id));
      if (status === "OPEN" && requests.length === 1 && page > 0) setPage(page - 1);
      else reload();
      setNotice({ tone: "success", text: "Request cancelled." });
    } catch (error) {
      setNotice({ tone: "critical", text: actionError(error, "The request could not be cancelled.") });
      reload();
    } finally {
      setBusy(false);
      setCancelling(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          eyebrow={text.eyebrow}
          title={text.title}
          action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun={text.noun} /> : undefined}
        />
        {notice ? (
          <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} className="mt-4" />
        ) : null}
        <div className="mt-4 grid gap-3 border-t border-border pt-4 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <SearchInput
            label={`Search ${text.title.toLowerCase()}`}
            placeholder="Search request details"
            maxLength={120}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <SelectField
            label="Filter by status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            options={[
              { value: "", label: "All statuses" },
              { value: "OPEN", label: "Open" },
              { value: "IN_PROGRESS", label: "In progress" },
              { value: "RESOLVED", label: "Resolved" },
              { value: "CANCELLED", label: "Cancelled" },
            ]}
          />
        </div>
        <div className="mt-4 border-t border-border">
          {resource.state === "loading" ? (
            <div aria-busy className="flex flex-col gap-3 py-4">
              <span role="status" className="sr-only">
                Loading {text.title.toLowerCase()}
              </span>
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          ) : resource.state === "error" ? (
            <StateMessage
              kind="error"
              layout="inline"
              className="mt-4"
              title={`${text.title} could not be loaded.`}
              action={
                <Button variant="secondary" size="sm" onClick={reload}>
                  Try again
                </Button>
              }
            />
          ) : requests.length ? (
            <ul aria-label={text.title} className="divide-y divide-line-soft">
              {requests.map((request) => (
                <li key={request.id} className="flex flex-wrap items-center gap-3 py-4">
                  <span aria-hidden className="inline-flex size-10 shrink-0 items-center justify-center rounded-sm bg-surface-soft text-green-800">
                    <text.icon size={20} strokeWidth={1.8} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink [overflow-wrap:anywhere]">{request.requestedService}</p>
                    <p className="text-caption text-text-secondary [overflow-wrap:anywhere]">
                      Requested {formatShortDate(request.createdAt)}
                      {request.scheduledAt ? ` · Visit ${formatWhen(request.scheduledAt)}` : ""}
                      {request.locationDescription ? ` · ${request.locationDescription}` : ""}
                    </p>
                  </div>
                  <ServiceRequestStatusBadge status={request.status} />
                  {request.status === "OPEN" ? (
                    <Button
                      variant="tertiary"
                      size="sm"
                      aria-label={`Cancel request: ${request.requestedService}`}
                      onClick={() => {
                        setNotice(null);
                        setCancelling(request);
                      }}
                    >
                      Cancel
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : (
            <StateMessage kind="empty" layout="inline" className="mt-4" title={text.empty} />
          )}
        </div>
        {resource.state === "ready" ? (
          <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label={`${text.title} pages`} />
        ) : null}
      </Card>

      <RequestForm
        kind={kind}
        onCreated={() => {
          setQuery("");
          setStatus("");
          if (page === 0 && !search && !status) reload();
          else setPage(0);
          setNotice({ tone: "success", text: "Request sent." });
        }}
      />

      <ConfirmDialog
        open={cancelling !== null}
        onClose={() => !busy && setCancelling(null)}
        onConfirm={() => void cancel()}
        loading={busy}
        tone="destructive"
        title="Cancel this request?"
        description={
          cancelling
            ? `“${cancelling.requestedService}”, requested ${formatDate(cancelling.createdAt)}. You can send a new request later.`
            : ""
        }
        confirmLabel="Cancel request"
      />
    </div>
  );
}

function RequestForm({ kind, onCreated }: { kind: Kind; onCreated: (request: ServiceRequest) => void }) {
  const text = copy[kind];
  const [service, setService] = useState("");
  const [location, setLocation] = useState("");
  const [errors, setErrors] = useState<{ service?: string; location?: string }>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = {
      service: service.trim() ? undefined : kind === "lab" ? "Describe the test you need." : "Describe the care you need.",
      location: kind === "home-care" && !location.trim() ? "Describe where the visit should take place." : undefined,
    };
    setErrors(next);
    setFailure("");
    if (next.service || next.location) {
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setBusy(true);
    try {
      const created =
        kind === "lab" ? await patientApi.requestLab(service.trim()) : await patientApi.requestHomeCare(service.trim(), location.trim());
      setService("");
      setLocation("");
      onCreated(created);
    } catch (error) {
      setFailure(actionError(error, "The request could not be sent."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={text.formTitle} titleAs="h3" />
      <p className="mt-1.5 text-data text-text-secondary">{text.formHelp}</p>
      <form noValidate onSubmit={submit} className="mt-5 flex flex-col gap-4">
        {kind === "lab" ? (
          <TextInput
            label="Test needed"
            required
            maxLength={LIMITS.testDescription}
            value={service}
            error={errors.service}
            description="For example, the test your doctor asked you to arrange."
            onChange={(event) => setService(event.target.value)}
          />
        ) : (
          <>
            <TextInput
              label="Care needed"
              required
              maxLength={LIMITS.serviceDescription}
              value={service}
              error={errors.service}
              onChange={(event) => setService(event.target.value)}
            />
            <TextArea
              label="Visit location"
              required
              maxLength={LIMITS.locationDescription}
              value={location}
              error={errors.location}
              description="Community, street and any landmark that helps the visitor find you."
              onChange={(event) => setLocation(event.target.value)}
            />
          </>
        )}
        <Alert tone="warning" title="Not for emergencies">
          MedNet does not replace emergency services.
        </Alert>
        {failure ? <Alert tone="critical" live="assertive" title={failure} /> : null}
        <div>
          <Button type="submit" loading={busy}>
            Send request
          </Button>
        </div>
      </form>
    </Card>
  );
}
