"use client";

import { CalendarPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppointmentStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog, Dialog, Drawer } from "@/components/ui/dialog";
import { RadioGroup } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { Tabs } from "@/components/ui/tabs";
import {
  CARE_TIME_ZONE,
  CARE_TIME_ZONE_LABEL,
  consultationModeLabel,
  formatDayHeading,
  formatSlot,
  formatTimeRange,
} from "@/features/find-care/format";
import type { Appointment } from "@/features/patient/patient-api";
import { formatDuration, formatWhen } from "@/features/patient/format";
import { CountBadge, ViewBand, ViewColumns, PrivacyCard } from "@/features/patient/view-frame";
import { ErrorRow } from "@/features/workspace/states";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { cn } from "@/lib/cn";
import { providerApi, type ProviderAppointmentAction } from "./provider-api";
import { useApprovedProvider } from "./provider-workspace";
import type { ProviderAppointmentTab } from "./tabs";

const tileDay = new Intl.DateTimeFormat("en-GB", { timeZone: CARE_TIME_ZONE, day: "numeric" });
const tileMonth = new Intl.DateTimeFormat("en-GB", { timeZone: CARE_TIME_ZONE, month: "short" });


/** Requests the provider must answer: new requests, and new times the patient asked for. */
export function needsResponse(appointment: Appointment) {
  return appointment.status === "PENDING" || (appointment.status === "RESCHEDULE_REQUESTED" && appointment.rescheduleRequestedBy === "PATIENT");
}

/** Groups the provider's appointments into the three tabs (Liberia time; "now" injectable for tests). */
export function groupProviderAppointments(appointments: Appointment[], now = new Date()) {
  const requests = appointments.filter(needsResponse).sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const upcoming = appointments
    .filter((a) => !needsResponse(a) && ["CONFIRMED", "RESCHEDULE_REQUESTED"].includes(a.status) && new Date(a.endsAt) > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const history = appointments
    .filter((a) => !requests.includes(a) && !upcoming.includes(a))
    .sort((a, b) => b.startsAt.localeCompare(a.startsAt));
  return { requests, upcoming, history };
}

type Pending = { appointment: Appointment; action: ProviderAppointmentAction };

const actionCopy: Record<Exclude<ProviderAppointmentAction, "REQUEST_RESCHEDULE">, { title: string; label: string; done: string; destructive: boolean }> = {
  ACCEPT: { title: "Confirm this appointment?", label: "Confirm appointment", done: "Appointment confirmed. The patient has been notified.", destructive: false },
  DECLINE: { title: "Decline this request?", label: "Decline request", done: "Request declined. The time is open again.", destructive: true },
  CANCEL: { title: "Cancel this appointment?", label: "Cancel appointment", done: "Appointment cancelled. The patient has been notified.", destructive: true },
  ACCEPT_RESCHEDULE: { title: "Accept the patient’s new time?", label: "Accept new time", done: "New time accepted.", destructive: false },
  DECLINE_RESCHEDULE: { title: "Keep the original time?", label: "Keep original time", done: "The original time was kept.", destructive: true },
  COMPLETE: { title: "Mark this appointment as completed?", label: "Mark completed", done: "Appointment marked as completed.", destructive: false },
};

export const providerConsultationHref = (appointmentId: string) =>
  `/provider/appointments/consultation?appointment=${encodeURIComponent(appointmentId)}`;

/** Which actions the backend allows for this appointment right now (AppointmentService state machine). */
export function availableActions(appointment: Appointment, now = new Date()) {
  const started = new Date(appointment.startsAt) <= now;
  const ended = new Date(appointment.endsAt) <= now;
  const status = appointment.status;
  return {
    accept: status === "PENDING",
    decline: status === "PENDING",
    propose: status === "CONFIRMED" && !started,
    respondToReschedule: status === "RESCHEDULE_REQUESTED" && appointment.rescheduleRequestedBy === "PATIENT",
    waitingForPatient: status === "RESCHEDULE_REQUESTED" && appointment.rescheduleRequestedBy === "PROVIDER",
    cancel: ["PENDING", "CONFIRMED", "RESCHEDULE_REQUESTED"].includes(status) && !started,
    complete: status === "CONFIRMED" && ended,
    consultation: status === "CONFIRMED" && appointment.consultationMode === "TEXT",
  };
}

export function ProviderAppointments({ tab, selectedId }: { tab: ProviderAppointmentTab; selectedId?: string }) {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const { resource, reload, replace } = useResource((signal) => providerApi.appointments(page, 25, signal), String(page));
  const [pending, setPending] = useState<Pending | null>(null);
  const [proposing, setProposing] = useState<Appointment | null>(null);
  const [detailId, setDetailId] = useState<string | undefined>(selectedId);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "critical"; text: string } | null>(null);
  const [now] = useState(() => new Date());

  function applyUpdate(updated: Appointment) {
    if (resource.state !== "ready") return reload();
    replace({ ...resource.data, content: resource.data.content.map((item) => (item.id === updated.id ? updated : item)) });
  }

  async function confirm() {
    if (!pending) return;
    setBusy(true);
    try {
      applyUpdate(await providerApi.act(pending.appointment.id, pending.action));
      setNotice({ tone: "success", text: actionCopy[pending.action as keyof typeof actionCopy].done });
    } catch (error) {
      // 409: the appointment changed since this list loaded (for example, the patient cancelled). Refresh it.
      setNotice({ tone: "critical", text: actionError(error, "The appointment could not be updated.") });
      reload();
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  const groups = resource.state === "ready" ? groupProviderAppointments(resource.data.content, now) : null;
  const act = (appointment: Appointment) => (action: ProviderAppointmentAction | "PROPOSE" | "DETAILS") => {
    setNotice(null);
    if (action === "PROPOSE") setProposing(appointment);
    else if (action === "DETAILS") setDetailId(appointment.id);
    else setPending({ appointment, action });
  };

  const list = (items: Appointment[], empty: string, label: string) =>
    items.length ? (
      <ul aria-label={label} className="mt-4 divide-y divide-line-soft border-t border-border">
        {items.map((appointment) => (
          <ProviderAppointmentRow key={appointment.id} appointment={appointment} now={now} selected={appointment.id === selectedId} onAct={act(appointment)} />
        ))}
      </ul>
    ) : (
      <StateMessage
        kind="empty"
        layout="inline"
        className="mt-4"
        title={resource.state === "ready" && resource.data.totalElements > 0 ? `${empty.replace(/\.$/, "")} on this page.` : empty}
      />
    );

  const panel = (title: string, items: Appointment[] | undefined, empty: string) => (
    <Card>
      <CardHeader eyebrow="Current view" title={title} action={items ? <CountBadge count={items.length} /> : undefined} />
      {resource.state === "loading" ? (
        <div aria-busy className="mt-4 flex flex-col gap-3">
          <span role="status" className="sr-only">
            Loading appointments
          </span>
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : resource.state === "error" ? (
        <ErrorRow title="Appointments could not be loaded." onRetry={reload} status={resource.status} />
      ) : (
        list(items ?? [], empty, title)
      )}
    </Card>
  );

  return (
    <>
      <ViewBand
        eyebrow="Doctor workspace"
        title="Appointments"
        description="Confirm requests, propose new times and keep your appointments up to date. Times are in Liberia time (GMT)."
        action={
          <ButtonLink href="/provider/schedule" variant="inverse" leadingIcon={<CalendarPlus aria-hidden size={18} />}>
            Publish open times
          </ButtonLink>
        }
      />
      <ViewColumns
        aside={
          <PrivacyCard>
            Before you confirm a request you see only the patient’s name and the requested time. Records open only with a
            confirmed appointment and the patient’s permission.
          </PrivacyCard>
        }
      >
        {notice ? <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} /> : null}
        {/* Only the active panel is mounted; all panels share one fresh list. */}
        <Tabs
          label="Appointment views"
          value={tab}
          onChange={(id) => router.replace(id === "requests" ? "/provider/appointments" : `/provider/appointments?tab=${id}`, { scroll: false })}
          items={[
            {
              id: "requests",
              label: groups ? `Requests (${groups.requests.length})` : "Requests",
              content: tab === "requests" ? panel("Requests to answer", groups?.requests, "No requests are waiting for you.") : null,
            },
            {
              id: "upcoming",
              label: "Upcoming",
              content: tab === "upcoming" ? panel("Upcoming appointments", groups?.upcoming, "No upcoming appointments.") : null,
            },
            {
              id: "history",
              label: "Completed and closed",
              content: tab === "history" ? panel("Completed and closed", groups?.history, "No earlier appointments.") : null,
            },
          ]}
        />
        {resource.state === "ready" && resource.data.totalPages > 1 ? (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
            <p className="text-caption text-text-secondary" aria-live="polite">
              Showing appointments {page * resource.data.size + 1}–{page * resource.data.size + resource.data.content.length} of {resource.data.totalElements}.
            </p>
            <Pagination
              page={page + 1}
              totalPages={resource.data.totalPages}
              onPageChange={(nextPage) => setPage(nextPage - 1)}
              label="Appointment history pages"
            />
          </div>
        ) : null}
      </ViewColumns>

      {detailId ? (
        <AppointmentDetailsDrawer
          id={detailId}
          now={now}
          onClose={() => setDetailId(undefined)}
          onAct={(appointment, action) => {
            setDetailId(undefined);
            act(appointment)(action);
          }}
        />
      ) : null}

      {proposing ? (
        <ProposeTimeDialog
          appointment={proposing}
          onClose={() => setProposing(null)}
          onProposed={(updated) => {
            applyUpdate(updated);
            setProposing(null);
            setNotice({ tone: "success", text: "New time proposed. The patient has been notified and can accept or decline." });
          }}
        />
      ) : null}

      <ConfirmDialog
        open={pending !== null}
        onClose={() => !busy && setPending(null)}
        onConfirm={() => void confirm()}
        loading={busy}
        tone={pending && actionCopy[pending.action as keyof typeof actionCopy].destructive ? "destructive" : "default"}
        title={pending ? actionCopy[pending.action as keyof typeof actionCopy].title : ""}
        description={
          pending
            ? `${pending.appointment.patientName}, ${formatSlot(pending.appointment.startsAt, pending.appointment.endsAt)} (${CARE_TIME_ZONE_LABEL}).`
            : ""
        }
        confirmLabel={pending ? actionCopy[pending.action as keyof typeof actionCopy].label : ""}
      />
    </>
  );
}

function ProviderAppointmentRow({
  appointment,
  now,
  selected,
  onAct,
}: {
  appointment: Appointment;
  now: Date;
  selected: boolean;
  onAct: (action: ProviderAppointmentAction | "PROPOSE" | "DETAILS") => void;
}) {
  const ref = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (selected) ref.current?.scrollIntoView({ block: "center" });
  }, [selected]);
  const can = availableActions(appointment, now);
  const name = appointment.patientName;
  const proposed = appointment.status === "RESCHEDULE_REQUESTED" && appointment.proposedStartsAt && appointment.proposedEndsAt;

  return (
    <li
      ref={ref}
      id={`appointment-${appointment.id}`}
      aria-current={selected ? "true" : undefined}
      className={cn("flex flex-col gap-4 py-4", selected && "-mx-3 rounded-sm bg-green-50/60 px-3 ring-1 ring-green-100")}
    >
      <div className="flex flex-wrap items-start gap-4">
        <p aria-hidden className="flex size-14 shrink-0 flex-col items-center justify-center rounded-sm bg-green-50 text-green-900">
          <span className="text-title leading-none font-bold">{tileDay.format(new Date(appointment.startsAt))}</span>
          <span className="text-caption">{tileMonth.format(new Date(appointment.startsAt))}</span>
        </p>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold text-ink [overflow-wrap:anywhere]">{name}</h3>
            <AppointmentStatusBadge status={appointment.status} />
          </div>
          <p className="mt-0.5 text-data text-text-secondary">
            {formatWhen(appointment.startsAt)} · {formatDuration(appointment.startsAt, appointment.endsAt)} · {consultationModeLabel(appointment.consultationMode)}
          </p>
          {appointment.status === "PENDING" ? <p className="mt-1 text-caption text-text-secondary">New request. The time is held until you answer.</p> : null}
          {can.complete ? <p className="mt-1 text-caption text-text-secondary">This appointment has ended.</p> : null}
        </div>
        <div className="flex flex-wrap gap-2">
          {can.accept ? (
            <Button size="sm" aria-label={`Confirm request from ${name}`} onClick={() => onAct("ACCEPT")}>
              Confirm
            </Button>
          ) : null}
          {can.decline ? (
            <Button size="sm" variant="secondary" aria-label={`Decline request from ${name}`} onClick={() => onAct("DECLINE")}>
              Decline
            </Button>
          ) : null}
          {can.consultation ? (
            <ButtonLink href={providerConsultationHref(appointment.id)} size="sm" aria-label={`Text consultation with ${name}`}>
              Text consultation
            </ButtonLink>
          ) : null}
          {can.complete ? (
            <Button size="sm" variant="secondary" aria-label={`Mark appointment with ${name} completed`} onClick={() => onAct("COMPLETE")}>
              Mark completed
            </Button>
          ) : null}
          {can.propose ? (
            <Button size="sm" variant="secondary" aria-label={`Propose a new time to ${name}`} onClick={() => onAct("PROPOSE")}>
              Propose new time
            </Button>
          ) : null}
          <Button size="sm" variant="secondary" aria-label={`Details of appointment with ${name}`} onClick={() => onAct("DETAILS")}>
            Details
          </Button>
          {can.cancel && !can.accept ? (
            <Button size="sm" variant="tertiary" aria-label={`Cancel appointment with ${name}`} onClick={() => onAct("CANCEL")}>
              Cancel
            </Button>
          ) : null}
        </div>
      </div>

      {proposed ? (
        <div className="rounded-sm border border-warning/40 bg-warning-soft/60 p-4">
          <p className="font-semibold text-ink">
            {appointment.rescheduleRequestedBy === "PATIENT" ? "The patient asked for a new time" : "You proposed a new time"}
          </p>
          <DescriptionList
            columns={2}
            className="mt-3"
            items={[
              { term: "Current time", detail: formatSlot(appointment.startsAt, appointment.endsAt) },
              { term: "Proposed time", detail: formatSlot(appointment.proposedStartsAt!, appointment.proposedEndsAt!) },
            ]}
          />
          {can.respondToReschedule ? (
            <div className="mt-4 flex flex-wrap gap-3">
              <Button size="sm" onClick={() => onAct("ACCEPT_RESCHEDULE")}>
                Accept new time
              </Button>
              <Button size="sm" variant="secondary" onClick={() => onAct("DECLINE_RESCHEDULE")}>
                Keep original time
              </Button>
            </div>
          ) : (
            <p className="mt-3 text-data text-text-secondary">Waiting for the patient to accept or decline.</p>
          )}
        </div>
      ) : null}
    </li>
  );
}

/** REQUEST_RESCHEDULE: the provider proposes one of their own open, future slots (AppointmentService). */
function ProposeTimeDialog({
  appointment,
  onClose,
  onProposed,
}: {
  appointment: Appointment;
  onClose: () => void;
  onProposed: (updated: Appointment) => void;
}) {
  const provider = useApprovedProvider();
  const { resource, reload } = useResource((signal) => providerApi.openSlots(provider.id, 0, 100, signal), provider.id);
  const [slotId, setSlotId] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const slots = resource.state === "ready" ? resource.data.content.filter((slot) => new Date(slot.startsAt) > new Date()) : [];

  async function propose() {
    if (!slotId) return setError("Choose a new time.");
    setBusy(true);
    setError("");
    try {
      onProposed(await providerApi.act(appointment.id, "REQUEST_RESCHEDULE", slotId));
    } catch (failure) {
      setError(actionError(failure, "The new time could not be proposed."));
      setSlotId("");
      reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      onClose={() => !busy && onClose()}
      title="Propose a new time"
      description={`For ${appointment.patientName}, currently ${formatSlot(appointment.startsAt, appointment.endsAt)}. Choose one of your open times (${CARE_TIME_ZONE_LABEL}).`}
      dismissible={!busy}
      footer={
        <>
          <Button variant="tertiary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={() => void propose()} loading={busy} disabled={!slots.length}>
            Propose this time
          </Button>
        </>
      }
    >
      {resource.state === "loading" ? (
        <div aria-busy>
          <span role="status" className="sr-only">
            Loading your open times
          </span>
          <Skeleton className="h-24 w-full" />
        </div>
      ) : resource.state === "error" ? (
        <ErrorRow title="Your open times could not be loaded." onRetry={reload} status={resource.status} />
      ) : slots.length ? (
        <div className="flex max-h-[45dvh] flex-col gap-4 overflow-y-auto">
          {error ? <Alert tone="critical" live="assertive" title={error} /> : null}
          <RadioGroup
            legend="Open times"
            name="proposed-slot"
            orientation="vertical"
            value={slotId}
            onChange={setSlotId}
            options={slots.map((slot) => ({
              value: slot.id,
              label: `${formatDayHeading(slot.startsAt)}, ${formatTimeRange(slot.startsAt, slot.endsAt)} · ${consultationModeLabel(slot.consultationMode)}`,
            }))}
          />
        </div>
      ) : (
        <StateMessage
          kind="empty"
          layout="inline"
          title="You have no open times to offer."
          action={<ButtonLink href="/provider/schedule" size="sm" variant="secondary">Publish open times</ButtonLink>}
        />
      )}
    </Dialog>
  );
}

function AppointmentDetailsDrawer({
  id,
  now,
  onClose,
  onAct,
}: {
  id: string;
  now: Date;
  onClose: () => void;
  onAct: (appointment: Appointment, action: ProviderAppointmentAction | "PROPOSE") => void;
}) {
  const { resource, reload } = useResource((signal) => providerApi.appointment(id, signal), id);
  const appointment = resource.state === "ready" ? resource.data : null;
  const can = appointment ? availableActions(appointment, now) : null;
  return (
    <Drawer
      open
      onClose={onClose}
      title="Appointment details"
      footer={
        appointment && can && (can.accept || can.consultation || can.cancel) ? (
          <>
            {can.accept ? <Button onClick={() => onAct(appointment, "ACCEPT")}>Confirm</Button> : null}
            {can.consultation ? <ButtonLink href={providerConsultationHref(appointment.id)}>Text consultation</ButtonLink> : null}
            {can.cancel ? (
              <Button variant="secondary" onClick={() => onAct(appointment, can.accept ? "DECLINE" : "CANCEL")}>
                {can.accept ? "Decline" : "Cancel appointment"}
              </Button>
            ) : null}
          </>
        ) : undefined
      }
    >
      {resource.state === "loading" ? (
        <div aria-busy className="flex flex-col gap-3">
          <span role="status" className="sr-only">
            Loading appointment details
          </span>
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : resource.state === "error" ? (
        resource.status === 404 ? (
          <StateMessage kind="empty" title="Appointment not found" description="It may belong to another provider, or it no longer exists." />
        ) : (
          <ErrorRow title="The appointment could not be loaded." onRetry={reload} status={resource.status} />
        )
      ) : appointment ? (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-title text-ink">{appointment.patientName}</p>
            <AppointmentStatusBadge status={appointment.status} />
          </div>
          <DescriptionList
            items={[
              { term: "Date and time", detail: formatSlot(appointment.startsAt, appointment.endsAt) },
              { term: "Time zone", detail: CARE_TIME_ZONE_LABEL },
              { term: "Duration", detail: formatDuration(appointment.startsAt, appointment.endsAt) },
              { term: "Consultation", detail: consultationModeLabel(appointment.consultationMode) },
              ...(appointment.proposedStartsAt && appointment.proposedEndsAt
                ? [
                    {
                      term: appointment.rescheduleRequestedBy === "PATIENT" ? "Time the patient asked for" : "Time you proposed",
                      detail: formatSlot(appointment.proposedStartsAt, appointment.proposedEndsAt),
                    },
                  ]
                : []),
              { term: "Requested", detail: formatWhen(appointment.createdAt) },
              { term: "Last updated", detail: formatWhen(appointment.updatedAt) },
              { term: "Reference", detail: <span className="font-mono text-caption">{appointment.id}</span> },
            ]}
          />
          {["CONFIRMED", "COMPLETED", "RESCHEDULE_REQUESTED"].includes(appointment.status) ? (
            <ButtonLink href={`/provider/patients/${encodeURIComponent(appointment.patientAccountId)}`} variant="secondary">
              Open patient
            </ButtonLink>
          ) : (
            <p className="text-caption text-text-secondary">Patient details open once the appointment is confirmed.</p>
          )}
        </div>
      ) : null}
    </Drawer>
  );
}
