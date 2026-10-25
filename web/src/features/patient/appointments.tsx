"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppointmentStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog, Drawer } from "@/components/ui/dialog";
import { DescriptionList } from "@/components/ui/lists";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { Tabs } from "@/components/ui/tabs";
import { CARE_TIME_ZONE, CARE_TIME_ZONE_LABEL, consultationModeLabel, formatSlot } from "@/features/find-care/format";
import { ApiRequestError } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatDuration, formatWhen, sortAppointments } from "./format";
import { patientApi, type Appointment, type PatientAppointmentAction } from "./patient-api";
import { ServiceRequestPanel } from "./service-requests";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { CountBadge, ViewBand, ViewColumns } from "./view-frame";

const tileDay = new Intl.DateTimeFormat("en-GB", { timeZone: CARE_TIME_ZONE, day: "numeric" });
const tileMonth = new Intl.DateTimeFormat("en-GB", { timeZone: CARE_TIME_ZONE, month: "short" });

type PendingAction = { appointment: Appointment; action: PatientAppointmentAction };

const confirmCopy: Record<PatientAppointmentAction, { title: string; label: string; done: string }> = {
  CANCEL: { title: "Cancel this appointment?", label: "Cancel appointment", done: "Appointment cancelled. The time is open to other patients again." },
  ACCEPT_RESCHEDULE: { title: "Accept the new time?", label: "Accept new time", done: "New time accepted." },
  DECLINE_RESCHEDULE: { title: "Keep the original time?", label: "Decline new time", done: "You kept the original time." },
};

/** Whether the patient may still cancel: the backend allows it until the appointment starts. */
function cancellable(appointment: Appointment, now: Date) {
  return ["PENDING", "CONFIRMED", "RESCHEDULE_REQUESTED"].includes(appointment.status) && new Date(appointment.startsAt) > now;
}

export function PatientAppointments({ tab, selectedId }: { tab: string; selectedId?: string }) {
  const router = useRouter();
  // Only the active panel is mounted, so each tab loads fresh data when opened.
  const active = tab === "home-care" ? "home-care" : "appointments";
  return (
    <>
      <ViewBand
        title="Appointments"
        description="Your appointment requests and visits with approved doctors, in Liberia time."
        action={
          <ButtonLink href="/patient/find-a-doctor" variant="inverse" leadingIcon={<Search aria-hidden size={18} />}>
            Find a doctor
          </ButtonLink>
        }
      />
      <ViewColumns>
        <Tabs
          label="Appointment views"
          value={active}
          onChange={(id) => router.replace(id === "home-care" ? "/patient/appointments?tab=home-care" : "/patient/appointments", { scroll: false })}
          items={[
            { id: "appointments", label: "Appointments", content: active === "appointments" ? <AppointmentList selectedId={selectedId} /> : null },
            { id: "home-care", label: "Home-care requests", content: active === "home-care" ? <ServiceRequestPanel kind="home-care" /> : null },
          ]}
        />
      </ViewColumns>
    </>
  );
}

function AppointmentList({ selectedId }: { selectedId?: string }) {
  const [page, setPage] = useState(0);
  const { resource, reload, replace } = useResource((signal) => patientApi.appointments(page, 25, signal), String(page));
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "critical"; text: string } | null>(null);
  const [now] = useState(() => new Date());
  // Opened from the overview's "View details" (?appointment=) or a row's Details button.
  const [detailId, setDetailId] = useState<string | undefined>(selectedId);

  async function confirm() {
    if (!pending || resource.state !== "ready") return;
    setBusy(true);
    try {
      const updated = await patientApi.actOnAppointment(pending.appointment.id, pending.action);
      replace({
        ...resource.data,
        content: resource.data.content.map((item) => (item.id === updated.id ? updated : item)),
      });
      setNotice({ tone: "success", text: confirmCopy[pending.action].done });
    } catch (error) {
      const forbidden = error instanceof ApiRequestError && error.isForbidden;
      setNotice({
        tone: "critical",
        text: forbidden
          ? "Only the doctor can respond to a change you proposed."
          : actionError(error, "The appointment could not be updated."),
      });
      reload();
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  if (resource.state === "loading") {
    return (
      <Card aria-busy>
        <span role="status" className="sr-only">
          Loading appointments
        </span>
        <Skeleton className="h-6 w-48" />
        <Skeleton className="mt-6 h-20 w-full" />
        <Skeleton className="mt-3 h-20 w-full" />
      </Card>
    );
  }
  if (resource.state === "error") {
    return (
      <StateMessage
        kind="error"
        title="Your appointments could not be loaded"
        description="The MedNet API did not respond. Try again shortly."
        action={
          <Button variant="secondary" size="sm" onClick={reload}>
            Try again
          </Button>
        }
      />
    );
  }

  const { upcoming, past } = sortAppointments(resource.data.content, now);
  const actions = (appointment: Appointment) => ({
    onAct: (action: PatientAppointmentAction) => {
      setNotice(null);
      setPending({ appointment, action });
    },
    canCancel: cancellable(appointment, now),
    onDetails: () => setDetailId(appointment.id),
  });

  return (
    <div className="flex flex-col gap-6">
      {notice ? <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} /> : null}

      <Card>
        <CardHeader eyebrow="Current view" title="Upcoming" action={<CountBadge count={upcoming.length} />} />
        {upcoming.length ? (
          <ul aria-label="Upcoming appointments" className="mt-4 divide-y divide-line-soft border-t border-border">
            {upcoming.map((appointment) => (
              <AppointmentRow key={appointment.id} appointment={appointment} selected={appointment.id === selectedId} {...actions(appointment)} />
            ))}
          </ul>
        ) : (
          <StateMessage
            kind="empty"
            className="mt-4"
            title={resource.data.totalElements > 0 ? "No upcoming appointments on this page" : "No upcoming appointments"}
            description="Find an approved doctor and request one of their open times."
            action={<ButtonLink href="/patient/find-a-doctor">Find a doctor</ButtonLink>}
          />
        )}
      </Card>

      <Card>
        <CardHeader eyebrow="History" title="Past and closed" action={<CountBadge count={past.length} />} />
        {past.length ? (
          <ul aria-label="Past and closed appointments" className="mt-4 divide-y divide-line-soft border-t border-border">
            {past.map((appointment) => (
              <AppointmentRow key={appointment.id} appointment={appointment} selected={appointment.id === selectedId} {...actions(appointment)} />
            ))}
          </ul>
        ) : (
          <StateMessage
            kind="empty"
            layout="inline"
            className="mt-4"
            title={resource.data.totalElements > 0 ? "No earlier visits on this page." : "No earlier visits."}
          />
        )}
      </Card>

      {resource.data.totalPages > 1 ? (
        <div className="flex flex-wrap items-center justify-between gap-4">
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

      {detailId ? (
        <AppointmentDetails
          id={detailId}
          onClose={() => setDetailId(undefined)}
          onCancel={(appointment) => {
            setDetailId(undefined);
            setNotice(null);
            setPending({ appointment, action: "CANCEL" });
          }}
          canCancel={(appointment) => cancellable(appointment, now)}
        />
      ) : null}

      <ConfirmDialog
        open={pending !== null}
        onClose={() => !busy && setPending(null)}
        onConfirm={() => void confirm()}
        loading={busy}
        tone={pending?.action === "ACCEPT_RESCHEDULE" ? "default" : "destructive"}
        title={pending ? confirmCopy[pending.action].title : ""}
        description={pending ? describe(pending) : ""}
        confirmLabel={pending ? confirmCopy[pending.action].label : ""}
      />
    </div>
  );
}

function describe({ appointment, action }: PendingAction) {
  const original = `${appointment.providerName}, ${formatSlot(appointment.startsAt, appointment.endsAt)} (${CARE_TIME_ZONE_LABEL})`;
  if (action === "CANCEL") return `${original}. The doctor is told that you cancelled.`;
  const proposed =
    appointment.proposedStartsAt && appointment.proposedEndsAt ? formatSlot(appointment.proposedStartsAt, appointment.proposedEndsAt) : "the proposed time";
  return action === "ACCEPT_RESCHEDULE"
    ? `Your appointment with ${appointment.providerName} moves to ${proposed}.`
    : `Your appointment with ${appointment.providerName} stays at ${formatSlot(appointment.startsAt, appointment.endsAt)}.`;
}

function AppointmentRow({
  appointment,
  selected,
  canCancel,
  onAct,
  onDetails,
}: {
  appointment: Appointment;
  selected: boolean;
  canCancel: boolean;
  onAct: (action: PatientAppointmentAction) => void;
  onDetails: () => void;
}) {
  const ref = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (selected) ref.current?.scrollIntoView({ block: "center" });
  }, [selected]);
  const reschedule = appointment.status === "RESCHEDULE_REQUESTED" && appointment.proposedStartsAt && appointment.proposedEndsAt;

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
            <h3 className="font-semibold text-ink [overflow-wrap:anywhere]">{appointment.providerName}</h3>
            <AppointmentStatusBadge status={appointment.status} />
          </div>
          <p className="mt-0.5 text-data text-text-secondary">
            {formatWhen(appointment.startsAt)} · {formatDuration(appointment.startsAt, appointment.endsAt)} ·{" "}
            {consultationModeLabel(appointment.consultationMode)}
          </p>
          {appointment.status === "PENDING" ? (
            <p className="mt-1 text-caption text-text-secondary">Waiting for the doctor to confirm.</p>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-2">
          {joinable(appointment) ? (
            <ButtonLink href={consultationHref(appointment.id)} size="sm" aria-label={`Text consultation with ${appointment.providerName}`}>
              Text consultation
            </ButtonLink>
          ) : null}
          <Button variant="secondary" size="sm" aria-label={`Details of appointment with ${appointment.providerName}`} onClick={onDetails}>
            Details
          </Button>
          {canCancel && !reschedule ? (
            <Button variant="tertiary" size="sm" aria-label={`Cancel appointment with ${appointment.providerName}`} onClick={() => onAct("CANCEL")}>
              Cancel
            </Button>
          ) : null}
        </div>
      </div>

      {reschedule ? (
        <div className="rounded-sm border border-warning/40 bg-warning-soft/60 p-4">
          <p className="font-semibold text-ink">
            {appointment.rescheduleRequestedBy === "PATIENT" ? "You asked for a new time" : "A new time was proposed"}
          </p>
          <DescriptionList
            columns={2}
            className="mt-3"
            items={[
              { term: "Current time", detail: formatSlot(appointment.startsAt, appointment.endsAt) },
              { term: "Proposed time", detail: formatSlot(appointment.proposedStartsAt!, appointment.proposedEndsAt!) },
            ]}
          />
          {appointment.rescheduleRequestedBy === "PATIENT" ? (
            <p className="mt-3 text-data text-text-secondary">Waiting for the doctor to accept or decline the change.</p>
          ) : null}
          <div className="mt-4 flex flex-wrap gap-3">
            {appointment.rescheduleRequestedBy !== "PATIENT" ? (
              <>
                <Button size="sm" onClick={() => onAct("ACCEPT_RESCHEDULE")}>
                  Accept new time
                </Button>
                <Button size="sm" variant="secondary" onClick={() => onAct("DECLINE_RESCHEDULE")}>
                  Keep original time
                </Button>
              </>
            ) : null}
            {canCancel ? (
              <Button size="sm" variant="tertiary" onClick={() => onAct("CANCEL")}>
                Cancel appointment
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
    </li>
  );
}

/** A text consultation can be opened or rejoined while the appointment is confirmed (ConsultationService). */
function joinable(appointment: Appointment) {
  return appointment.status === "CONFIRMED" && appointment.consultationMode === "TEXT";
}

const consultationHref = (appointmentId: string) =>
  `/patient/appointments/consultation?appointment=${encodeURIComponent(appointmentId)}`;

/** Appointment details, loaded fresh from GET /appointments/{id} (ownership checked by the API). */
function AppointmentDetails({
  id,
  onClose,
  onCancel,
  canCancel,
}: {
  id: string;
  onClose: () => void;
  onCancel: (appointment: Appointment) => void;
  canCancel: (appointment: Appointment) => boolean;
}) {
  const { resource, reload } = useResource((signal) => patientApi.appointment(id, signal), id);
  const appointment = resource.state === "ready" ? resource.data : null;
  return (
    <Drawer
      open
      onClose={onClose}
      title="Appointment details"
      footer={
        appointment && (joinable(appointment) || canCancel(appointment)) ? (
          <>
            {joinable(appointment) ? <ButtonLink href={consultationHref(appointment.id)}>Text consultation</ButtonLink> : null}
            {canCancel(appointment) ? (
              <Button variant="secondary" onClick={() => onCancel(appointment)}>
                Cancel appointment
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
          <StateMessage kind="empty" title="Appointment not found" description="It may belong to another account, or it no longer exists." />
        ) : (
          <StateMessage
            kind={resource.status === 403 ? "denied" : "error"}
            title={resource.status === 403 ? "You do not have permission to see this appointment." : "The appointment could not be loaded."}
            action={
              resource.status === 403 ? undefined : (
                <Button variant="secondary" size="sm" onClick={reload}>
                  Try again
                </Button>
              )
            }
          />
        )
      ) : appointment ? (
        <div className="flex flex-col gap-5">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-title text-ink">{appointment.providerName}</p>
            <AppointmentStatusBadge status={appointment.status} />
          </div>
          <DescriptionList
            items={[
              { term: "Date and time", detail: formatSlot(appointment.startsAt, appointment.endsAt) },
              { term: "Time zone", detail: CARE_TIME_ZONE_LABEL },
              { term: "Duration", detail: formatDuration(appointment.startsAt, appointment.endsAt) },
              { term: "Consultation", detail: consultationModeLabel(appointment.consultationMode) },
              ...(appointment.proposedStartsAt && appointment.proposedEndsAt
                ? [{ term: "Proposed new time", detail: formatSlot(appointment.proposedStartsAt, appointment.proposedEndsAt) }]
                : []),
              { term: "Requested", detail: formatWhen(appointment.createdAt) },
              { term: "Last updated", detail: formatWhen(appointment.updatedAt) },
              { term: "Reference", detail: <span className="font-mono text-caption">{appointment.id}</span> },
            ]}
          />
          <p className="text-caption text-text-secondary">{statusExplanation(appointment.status)}</p>
        </div>
      ) : null}
    </Drawer>
  );
}

/** Plain-language meaning of each backend status (appointments.status, V6 migration). */
function statusExplanation(status: string): string {
  switch (status) {
    case "PENDING":
      return "The doctor has not confirmed this request yet. The time is held for you.";
    case "CONFIRMED":
      return "The doctor confirmed this appointment. You can cancel until it starts.";
    case "RESCHEDULE_REQUESTED":
      return "A new time was proposed. The appointment keeps its current time until the change is accepted.";
    case "DECLINED":
      return "The doctor declined this request. You can request another time.";
    case "CANCELLED":
      return "This appointment was cancelled.";
    case "COMPLETED":
      return "The doctor marked this appointment as completed.";
    default:
      return "";
  }
}
