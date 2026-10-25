"use client";

import { useState, type FormEvent } from "react";
import { ServiceRequestStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { ConfirmDialog, Dialog } from "@/components/ui/dialog";
import { SelectField, TextInput } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { Pagination } from "@/components/ui/pagination";
import { StateMessage } from "@/components/ui/state-message";
import { Tabs } from "@/components/ui/tabs";
import { LIST_PAGE_SIZE, usePageFor } from "@/features/workspace/paging";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { CsvExportButton } from "./csv-export-button";
import { exportAllOperationalReferences, exportAllPatientRequests } from "./csv-export";
import {
  ADMIN_LIMITS,
  OPERATIONAL_REQUEST_TYPES,
  adminApi,
  type OperationalRequest,
  type PatientServiceRequestSummary,
} from "./admin-api";
import {
  ListCard,
  NoRecordSelected,
  RecordLayout,
  SelectableList,
  SelectableRow,
  SelectedRecord,
  formatAdminDateTime,
} from "./frame";
import { REQUEST_TYPE_LABELS, shortReference } from "./permissions";

type Notice = { tone: "success" | "critical"; text: string } | null;

function NoticeAlert({ notice, onDismiss }: { notice: Notice; onDismiss: () => void }) {
  if (!notice) return null;
  return (
    <Alert
      tone={notice.tone}
      live={notice.tone === "critical" ? "assertive" : "polite"}
      title={notice.text}
      action={
        <Button size="sm" variant="tertiary" onClick={onDismiss}>
          Dismiss
        </Button>
      }
    />
  );
}

/** Task queue: patient home-care and laboratory requests, plus admin-entered operational references. */
export function TaskQueue() {
  return (
    <Tabs
      label="Task queues"
      items={[
        { id: "home-care", label: "Home-care requests", content: <PatientRequestQueue type="HOME_CARE" /> },
        { id: "laboratory", label: "Laboratory requests", content: <PatientRequestQueue type="LABORATORY" /> },
        { id: "references", label: "Operational references", content: <OperationalReferences /> },
      ]}
    />
  );
}

const PATIENT_TYPE: Record<"HOME_CARE" | "LABORATORY", { noun: string; staff: string; staffLabel: string }> = {
  HOME_CARE: { noun: "home-care request", staff: "HOME_CARE", staffLabel: "home-care staff" },
  LABORATORY: { noun: "laboratory request", staff: "LABORATORY", staffLabel: "laboratory staff" },
};

/**
 * Patient service requests as administrators see them (`AdminRequestSummary`): reference, type,
 * status and date only. Requested services, locations and results stay with the patient and the
 * assigned partner.
 */
function PatientRequestQueue({ type }: { type: "HOME_CARE" | "LABORATORY" }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [status, setStatus] = useState("open");
  const [notice, setNotice] = useState<Notice>(null);
  const meta = PATIENT_TYPE[type];
  const [page, setPage] = usePageFor(status);
  // Status filter and paging run on the server across every request (Phase 5D; was the latest 100).
  const statuses = status === "open" ? ["OPEN", "IN_PROGRESS"] : status ? [status] : [];
  const { resource, reload, replace } = useResource(
    (signal) => adminApi.serviceRequestsPage(type, statuses, page, LIST_PAGE_SIZE, signal),
    JSON.stringify([type, status, page]),
  );

  const rows = resource.state === "ready" ? resource.data.content : [];
  const selected = rows.find((request) => request.id === selectedId) ?? null;

  function changed(saved: PatientServiceRequestSummary, text: string) {
    // The row stays visible after a change, even if it no longer matches the status filter.
    if (resource.state === "ready") replace({ ...resource.data, content: resource.data.content.map((row) => (row.id === saved.id ? saved : row)) });
    setNotice({ tone: "success", text });
  }

  return (
    <div className="flex flex-col gap-6">
      <NoticeAlert notice={notice} onDismiss={() => setNotice(null)} />
      <RecordLayout
        list={
          <ListCard eyebrow="Operations queue" title={type === "HOME_CARE" ? "Home-care requests" : "Laboratory requests"}>
            <div className="flex justify-end">
              <CsvExportButton
                filename={type === "HOME_CARE" ? "mednet-home-care-requests" : "mednet-laboratory-requests"}
                headers={["Request", "Status", "Received", "Assigned to"]}
                disabled={resource.state !== "ready"}
                loadRows={async () =>
                  (await exportAllPatientRequests(type, statuses)).map((request) => [
                    shortReference(request.id),
                    request.status,
                    formatAdminDateTime(request.createdAt),
                    request.assignedStaffPublicId || "Not assigned",
                  ])
                }
              />
            </div>
            <SelectField
              label="Request status"
              hideLabel
              fieldClassName="md:max-w-60"
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              options={[
                { value: "open", label: "Open and in progress" },
                { value: "", label: "Status: all" },
                { value: "OPEN", label: "Open" },
                { value: "IN_PROGRESS", label: "In progress" },
                { value: "RESOLVED", label: "Resolved" },
                { value: "CANCELLED", label: "Cancelled" },
              ]}
            />
            {resource.state === "loading" ? (
              <LoadingRows label={`Loading ${meta.noun}s`} />
            ) : resource.state === "error" ? (
              <ErrorRow title={`The ${meta.noun} queue could not be loaded`} status={resource.status} onRetry={reload} />
            ) : rows.length === 0 ? (
              <StateMessage kind="empty" title={status === "open" ? `No open ${meta.noun}s` : `No ${meta.noun}s match`} />
            ) : (
              <SelectableList label={`${meta.noun}s`}>
                {rows.map((request) => (
                  <SelectableRow
                    key={request.id}
                    selected={request.id === selectedId}
                    onSelect={() => setSelectedId(request.id)}
                    marker={type === "HOME_CARE" ? "HC" : "LAB"}
                    title={`Request ${shortReference(request.id)}`}
                    meta={`Received ${formatAdminDateTime(request.createdAt)} · ${request.assignedStaffPublicId ? `Assigned to ${request.assignedStaffPublicId}` : "Not assigned"}`}
                    trailing={<ServiceRequestStatusBadge status={request.status} />}
                  />
                ))}
              </SelectableList>
            )}
            {resource.state === "ready" ? (
              <Pagination
                page={resource.data.number + 1}
                totalPages={resource.data.totalPages}
                onPageChange={(next) => setPage(next - 1)}
                label={`${meta.noun} pages`}
              />
            ) : null}
          </ListCard>
        }
        record={
          selected ? (
            <PatientRequestRecord
              key={selected.id}
              request={selected}
              type={type}
              onChanged={changed}
              onFailed={(text) => setNotice({ tone: "critical", text })}
            />
          ) : (
            <NoRecordSelected>Open a request to coordinate it.</NoRecordSelected>
          )
        }
      />
    </div>
  );
}

function PatientRequestRecord({
  request,
  type,
  onChanged,
  onFailed,
}: {
  request: PatientServiceRequestSummary;
  type: "HOME_CARE" | "LABORATORY";
  onChanged: (request: PatientServiceRequestSummary, text: string) => void;
  onFailed: (text: string) => void;
}) {
  const meta = PATIENT_TYPE[type];
  const [pending, setPending] = useState<"IN_PROGRESS" | "CANCELLED" | null>(null);
  const [busy, setBusy] = useState(false);
  const [staffId, setStaffId] = useState("");
  const [assignError, setAssignError] = useState("");
  const [assigning, setAssigning] = useState(false);
  const open = request.status === "OPEN";
  const assigned = Boolean(request.assignedStaffPublicId);
  const assignable = open && !assigned;
  const cancellable = open || request.status === "IN_PROGRESS";
  const staff = useResource(
    (signal) => adminApi.accounts(0, 100, { accountType: meta.staff, status: "ACTIVE", emailVerified: "true" }, signal),
    assignable ? `staff-${meta.staff}` : "closed",
  );
  const staffOptions = assignable && staff.resource.state === "ready" ? staff.resource.data.content : [];

  async function changeStatus() {
    if (!pending) return;
    setBusy(true);
    try {
      const saved = await adminApi.changeServiceRequestStatus(request.id, pending);
      onChanged(saved, pending === "CANCELLED" ? "The request was cancelled and the patient was notified." : "The request is in progress and the patient was notified.");
    } catch (error) {
      onFailed(actionError(error, "The request could not be updated."));
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  async function assign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!staffId) {
      setAssignError(`Choose ${meta.staffLabel}.`);
      return;
    }
    setAssignError("");
    setAssigning(true);
    try {
      const result = await adminApi.assignServiceRequest(request.id, staffId);
      const chosen = staffOptions.find((account) => account.id === staffId);
      onChanged(
        { ...request, status: result.status, assignedStaffPublicId: chosen?.publicId ?? request.assignedStaffPublicId },
        `The request was assigned. The ${meta.staffLabel} member was notified.`,
      );
    } catch (error) {
      setAssignError(actionError(error, "The request could not be assigned."));
    } finally {
      setAssigning(false);
    }
  }

  return (
    <>
      <SelectedRecord
        focusKey={request.id}
        reference={`Request ${shortReference(request.id)}`}
        title={type === "HOME_CARE" ? "Home-care request" : "Laboratory request"}
        actions={
          <>
            {assignable ? (
              <Button size="sm" variant="secondary" onClick={() => setPending("IN_PROGRESS")}>
                Mark in progress
              </Button>
            ) : null}
            {cancellable ? (
              <Button size="sm" variant="destructive" onClick={() => setPending("CANCELLED")}>
                Cancel request
              </Button>
            ) : null}
          </>
        }
        restricted="Administrators see the reference, status and dates only. What was requested, where, and any results stay with the patient and the assigned partner."
      >
        <DescriptionList
          items={[
            { term: "Status", detail: <ServiceRequestStatusBadge status={request.status} /> },
            { term: "Received", detail: formatAdminDateTime(request.createdAt) },
            {
              term: "Assigned to",
              detail: assigned ? request.assignedStaffPublicId : "Not assigned",
            },
          ]}
        />
        {assigned && open ? (
          <p className="text-caption text-text-secondary">
            The assigned {meta.staffLabel} member moves this request on. Find them under {meta.staffLabel} accounts by this public ID.
          </p>
        ) : null}
        {assignable ? (
          <form noValidate onSubmit={assign} className="flex flex-col gap-3 border-t border-border pt-5">
            {staff.resource.state === "error" ? (
              <ErrorRow title={`The ${meta.staffLabel} list could not be loaded`} status={staff.resource.status} onRetry={staff.reload} />
            ) : (
              <SelectField
                label={`Assign to ${meta.staffLabel}`}
                description="Active accounts with a verified email address. A request can be assigned once."
                value={staffId}
                error={assignError || undefined}
                disabled={staff.resource.state !== "ready"}
                placeholder={staff.resource.state === "ready" ? (staffOptions.length ? "Choose a person" : `No ${meta.staffLabel} available`) : "Loading…"}
                onChange={(event) => setStaffId(event.target.value)}
                options={staffOptions.map((account) => ({ value: account.id, label: `${account.displayName || account.email} (${account.publicId})` }))}
              />
            )}
            <div>
              <Button type="submit" size="sm" loading={assigning} disabled={!staffOptions.length}>
                Assign request
              </Button>
            </div>
          </form>
        ) : null}
      </SelectedRecord>
      {pending ? (
        <ConfirmDialog
          open
          onClose={() => setPending(null)}
          onConfirm={() => void changeStatus()}
          title={pending === "CANCELLED" ? `Cancel request ${shortReference(request.id)}?` : `Mark request ${shortReference(request.id)} in progress?`}
          description={
            pending === "CANCELLED"
              ? "The patient is notified that the request was cancelled. This cannot be reopened."
              : "Use this when MedNet is handling the request without assigning partner staff. The patient is notified. Assigned requests are moved on by the partner."
          }
          confirmLabel={pending === "CANCELLED" ? "Cancel request" : "Mark in progress"}
          tone={pending === "CANCELLED" ? "destructive" : "default"}
          loading={busy}
        />
      ) : null}
    </>
  );
}

/** Admin-entered operational references (legacy "Requests"): no clinical details. */
function OperationalReferences() {
  const [page, setPage] = useState(0);
  const { resource, reload, replace } = useResource((signal) => adminApi.requestsPage(page, LIST_PAGE_SIZE, signal), `operational-${page}`);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const [busy, setBusy] = useState(false);
  const rows = resource.state === "ready" ? resource.data.content : [];
  const selected = rows.find((request) => request.id === selectedId) ?? null;

  async function move(request: OperationalRequest, status: "IN_PROGRESS" | "RESOLVED") {
    setBusy(true);
    try {
      const saved = await adminApi.changeRequestStatus(request.id, status);
      if (resource.state === "ready") replace({ ...resource.data, content: resource.data.content.map((row) => (row.id === saved.id ? saved : row)) });
      setNotice({ tone: "success", text: status === "RESOLVED" ? `${saved.referenceId} is resolved.` : `${saved.referenceId} is in progress.` });
    } catch (error) {
      setNotice({ tone: "critical", text: actionError(error, "The reference could not be updated.") });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <NoticeAlert notice={notice} onDismiss={() => setNotice(null)} />
      <RecordLayout
        list={
          <ListCard
            eyebrow="Operations queue"
            title="Operational references"
            action={
              <div className="flex flex-wrap items-center gap-2">
                <CsvExportButton
                  filename="mednet-operational-references"
                  headers={["Reference", "Type", "Status", "Added"]}
                  disabled={resource.state !== "ready"}
                  loadRows={async () =>
                    (await exportAllOperationalReferences()).map((request) => [
                      request.referenceId,
                      REQUEST_TYPE_LABELS[request.requestType] ?? request.requestType,
                      request.status,
                      formatAdminDateTime(request.createdAt),
                    ])
                  }
                />
                <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
                  Add reference
                </Button>
              </div>
            }
          >
            <p className="text-caption text-text-secondary">
              References entered by administrators to track work outside MedNet&apos;s patient flows. Do not enter symptoms,
              results or other clinical details.
            </p>
            {resource.state === "loading" ? (
              <LoadingRows label="Loading operational references" />
            ) : resource.state === "error" ? (
              <ErrorRow title="Operational references could not be loaded" status={resource.status} onRetry={reload} />
            ) : rows.length === 0 ? (
              <StateMessage kind="empty" title="No operational references yet" />
            ) : (
              <SelectableList label="Operational references">
                {rows.map((request) => (
                  <SelectableRow
                    key={request.id}
                    selected={request.id === selectedId}
                    onSelect={() => setSelectedId(request.id)}
                    marker={(REQUEST_TYPE_LABELS[request.requestType] ?? request.requestType).slice(0, 3).toUpperCase()}
                    title={request.referenceId}
                    meta={`${REQUEST_TYPE_LABELS[request.requestType] ?? request.requestType} · ${formatAdminDateTime(request.createdAt)}`}
                    trailing={<ServiceRequestStatusBadge status={request.status} />}
                  />
                ))}
              </SelectableList>
            )}
            {resource.state === "ready" ? (
              <Pagination
                page={resource.data.number + 1}
                totalPages={resource.data.totalPages}
                onPageChange={(next) => setPage(next - 1)}
                label="Operational reference pages"
              />
            ) : null}
          </ListCard>
        }
        record={
          selected ? (
            <SelectedRecord
              key={selected.id}
              focusKey={selected.id}
              reference={selected.referenceId}
              title={REQUEST_TYPE_LABELS[selected.requestType] ?? selected.requestType}
              actions={
                selected.status === "OPEN" ? (
                  <Button size="sm" loading={busy} onClick={() => void move(selected, "IN_PROGRESS")}>
                    Start
                  </Button>
                ) : selected.status === "IN_PROGRESS" ? (
                  <Button size="sm" loading={busy} onClick={() => void move(selected, "RESOLVED")}>
                    Resolve
                  </Button>
                ) : null
              }
            >
              <DescriptionList
                items={[
                  { term: "Status", detail: <ServiceRequestStatusBadge status={selected.status} /> },
                  { term: "Requester email", detail: selected.requesterEmail },
                  { term: "Added", detail: formatAdminDateTime(selected.createdAt) },
                  { term: "Last updated", detail: formatAdminDateTime(selected.updatedAt) },
                ]}
              />
            </SelectedRecord>
          ) : (
            <NoRecordSelected>Open a reference to start or resolve it.</NoRecordSelected>
          )
        }
      />
      <AddReferenceDialog
        open={adding}
        onClose={() => setAdding(false)}
        onAdded={(request) => {
          setAdding(false);
          setNotice({ tone: "success", text: `${request.referenceId} was added.` });
          setSelectedId(request.id);
          reload();
        }}
      />
    </div>
  );
}

function AddReferenceDialog({
  open,
  onClose,
  onAdded,
}: {
  open: boolean;
  onClose: () => void;
  onAdded: (request: OperationalRequest) => void;
}) {
  const empty = { referenceId: "", requestType: "APPOINTMENT", requesterEmail: "" };
  const [values, setValues] = useState(empty);
  const [errors, setErrors] = useState<{ referenceId?: string; requesterEmail?: string }>({});
  const [failure, setFailure] = useState("");
  const [busy, setBusy] = useState(false);

  function close() {
    setValues(empty);
    setErrors({});
    setFailure("");
    onClose();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = {
      referenceId: values.referenceId.trim() ? undefined : "Enter a reference.",
      requesterEmail: /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.requesterEmail.trim()) ? undefined : "Enter a valid email address.",
    };
    setErrors(next);
    setFailure("");
    if (next.referenceId || next.requesterEmail) {
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setBusy(true);
    try {
      const saved = await adminApi.createRequest({
        referenceId: values.referenceId.trim(),
        requestType: values.requestType,
        requesterEmail: values.requesterEmail.trim(),
      });
      setValues(empty);
      onAdded(saved);
    } catch (error) {
      setFailure(actionError(error, "The reference could not be added."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      dismissible={!busy}
      title="Add an operational reference"
      description="Operational references only. Do not enter symptoms, results or other clinical details."
      footer={
        <>
          <Button variant="tertiary" onClick={close} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" form="add-operational-reference" loading={busy}>
            Add reference
          </Button>
        </>
      }
    >
      <form id="add-operational-reference" noValidate onSubmit={submit} className="flex flex-col gap-4">
        <TextInput label="Reference" required maxLength={ADMIN_LIMITS.referenceId} value={values.referenceId} error={errors.referenceId} onChange={(event) => setValues({ ...values, referenceId: event.target.value })} />
        <SelectField
          label="Request type"
          value={values.requestType}
          options={OPERATIONAL_REQUEST_TYPES.map((type) => ({ value: type, label: REQUEST_TYPE_LABELS[type] }))}
          onChange={(event) => setValues({ ...values, requestType: event.target.value })}
        />
        <TextInput
          label="Requester email"
          type="email"
          autoComplete="off"
          required
          maxLength={ADMIN_LIMITS.email}
          value={values.requesterEmail}
          error={errors.requesterEmail}
          onChange={(event) => setValues({ ...values, requesterEmail: event.target.value })}
        />
        {failure ? <Alert tone="critical" live="assertive" title={failure} /> : null}
      </form>
    </Dialog>
  );
}
