"use client";

import { ArrowRight, Bell, CalendarClock, ClipboardList, MapPin, MessageSquareText, UserRound } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AppointmentSummary } from "@/components/healthcare/appointment";
import { MedicalRecordList, type MedicalRecordItem, type MedicalRecordKind } from "@/components/healthcare/records";
import { LabResultStatusBadge, ServiceRequestStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { FictionalDataBadge } from "@/components/ui/fictional-data-badge";
import { ActivityList, ActivityRow } from "@/components/ui/lists";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { WelcomeBand } from "@/components/workspace/workspace-sections";
import { CARE_TIME_ZONE_LABEL, consultationModeLabel } from "@/features/find-care/format";
import { FICTIONAL_RECEIPTS } from "./fictional";
import { formatDate, formatDuration, formatShortDate, formatWhen, nextAppointment, plural, recordCategoryLabel } from "./format";
import { patientApi, type ClinicalRecord, type LabResult, type Notification, type ServiceRequest } from "./patient-api";
import { usePatient } from "./patient-workspace";
import { actionError, useResource, type Resource } from "@/features/workspace/use-resource";

const recordKinds: Record<string, MedicalRecordKind> = {
  ENCOUNTER_SUMMARY: "visit-summary",
  PRESCRIPTION: "prescription",
  LAB_RESULT: "lab-result",
};

/** Newest clinical records and released lab results, merged (the reference's "Recent activity"). */
export function recentMedicalActivity(records: ClinicalRecord[], results: LabResult[], limit = 4): MedicalRecordItem[] {
  const fromRecords = records.map((record) => ({
    at: record.effectiveAt,
    item: {
      id: `record-${record.id}`,
      kind: recordKinds[record.category] ?? "document",
      title: record.title,
      meta: `${recordCategoryLabel(record.category)} · ${formatShortDate(record.effectiveAt)}`,
      href: `/patient/records?record=${encodeURIComponent(record.id)}`,
    } satisfies MedicalRecordItem,
  }));
  const fromResults = results.map((result) => ({
    at: result.reviewedAt ?? result.createdAt,
    item: {
      id: `lab-${result.id}`,
      kind: "lab-result",
      title: "Laboratory result",
      meta: `Released ${formatShortDate(result.reviewedAt ?? result.createdAt)}`,
      status: <LabResultStatusBadge status={result.status} />,
      href: "/patient/records?tab=laboratory",
    } satisfies MedicalRecordItem,
  }));
  return [...fromRecords, ...fromResults]
    .sort((a, b) => b.at.localeCompare(a.at))
    .slice(0, limit)
    .map(({ item }) => item);
}

export function PatientOverview() {
  const { profile } = usePatient();
  const appointments = useResource((signal) => patientApi.appointments(0, 100, signal));
  const medical = useResource((signal) =>
    Promise.all([patientApi.records(0, 4, signal), patientApi.labResults(0, 4, signal)]).then(([records, results]) =>
      recentMedicalActivity(records.content, results.content),
    ),
  );
  const requests = useResource((signal) =>
    Promise.all([
      patientApi.labRequestsPage(0, 4, {}, signal),
      patientApi.homeCareRequestsPage(0, 4, {}, signal),
    ]).then(([lab, homeCare]) => ({ lab: lab.content, homeCare: homeCare.content })),
  );
  const notifications = useResource((signal) => patientApi.notifications(0, 5, signal));

  const appointmentList = appointments.resource.state === "ready" ? appointments.resource.data.content : [];
  const next = nextAppointment(appointmentList);
  const rescheduleCount = appointmentList.filter((appointment) => appointment.status === "RESCHEDULE_REQUESTED").length;
  const unread =
    notifications.resource.state === "ready" ? notifications.resource.data.content.filter((item) => !item.readAt).length : 0;

  const actions = [
    profile.state === "missing"
      ? {
          id: "profile",
          icon: UserRound,
          title: "Complete your patient profile",
          meta: "Needed before you can request an appointment",
          href: "/patient/profile",
        }
      : null,
    rescheduleCount
      ? {
          id: "reschedule",
          icon: CalendarClock,
          title: rescheduleCount === 1 ? "Review a proposed new time" : `Review ${rescheduleCount} proposed new times`,
          meta: "An appointment is waiting for a response",
          href: "/patient/appointments",
        }
      : null,
  ].filter((action) => action !== null);

  return (
    <>
      <WelcomeBand
        eyebrow="Your care at a glance"
        title={
          appointments.resource.state !== "ready"
            ? "Your care, in one place."
            : next
              ? next.status === "CONFIRMED"
                ? "Everything is ready for your next visit."
                : "Your next appointment is waiting on a response."
              : "You have no upcoming appointments."
        }
        action={
          <ButtonLink href="/patient/find-a-doctor" variant="inverse" trailingIcon={<ArrowRight aria-hidden size={18} />}>
            Find a doctor
          </ButtonLink>
        }
      />

      {/* Two independent columns from 1200px (no row gaps); below that one column in reading priority. */}
      <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[minmax(0,1.95fr)_minmax(0,1fr)] lg:items-start">
        <div className="contents lg:flex lg:min-w-0 lg:flex-col lg:gap-6">
          <div className="order-1 min-w-0 lg:order-none">
            <NextAppointmentCard resource={appointments.resource} next={next} onRetry={appointments.reload} />
          </div>
          <div className="order-3 min-w-0 lg:order-none">
            <Card>
              <CardHeader
                eyebrow="Medical information"
                title="Recent activity"
                action={<ViewAll href="/patient/records" label="View all medical information" />}
              />
              <div className="mt-4 border-t border-border">
                {medical.resource.state === "loading" ? (
                  <ListSkeleton label="Loading medical information" />
                ) : medical.resource.state === "error" ? (
                  <StateMessage
                    kind="error"
                    layout="inline"
                    className="mt-4"
                    title="Your medical information could not be loaded."
                    action={<RetryButton onClick={medical.reload} />}
                  />
                ) : medical.resource.data.length ? (
                  <MedicalRecordList records={medical.resource.data} label="Recent medical information" />
                ) : (
                  <StateMessage
                    kind="empty"
                    layout="inline"
                    className="mt-4"
                    title="No records yet. Visit summaries, prescriptions and released results appear here."
                  />
                )}
              </div>
            </Card>
          </div>
          <div className="order-4 min-w-0 lg:order-none">
            <RequestsCard resource={requests.resource} onRetry={requests.reload} />
          </div>
        </div>
        <div className="contents lg:flex lg:min-w-0 lg:flex-col lg:gap-6">
          <div className="order-2 min-w-0 lg:order-none">
            <Card>
              <CardHeader
                eyebrow="For you"
                title={
                  profile.state === "loading" || appointments.resource.state === "loading"
                    ? "Checking for actions…"
                    : actions.length
                      ? `${plural(actions.length, "action")} to complete`
                      : "Nothing needs your attention"
                }
              />
              {actions.length ? (
                <div className="mt-4 border-t border-border">
                  <ActivityList label="Actions to complete">
                    {actions.map((action) => (
                      <ActivityRow key={action.id} icon={action.icon} tileTone="warning" title={action.title} meta={action.meta} href={action.href} />
                    ))}
                  </ActivityList>
                </div>
              ) : (
                <p className="mt-3 text-data text-text-secondary">
                  Actions such as completing your profile or answering a proposed new time appear here.
                </p>
              )}
            </Card>
          </div>
          <div className="order-6 min-w-0 lg:order-none">
            <div className="flex min-w-0 flex-col gap-6">
              <Card>
                <CardHeader eyebrow="Payments" title="Latest receipt" action={<FictionalDataBadge />} />
                <p className="mt-5 text-[1.75rem] leading-9 font-bold text-green-900">{FICTIONAL_RECEIPTS.data[0].amount}</p>
                <p className="text-caption text-text-secondary">
                  {FICTIONAL_RECEIPTS.data[0].description} · {FICTIONAL_RECEIPTS.data[0].date}
                </p>
                <p className="mt-3 text-caption text-text-secondary">
                  MedNet does not take payments. This card shows example data from the design.
                </p>
                <Link
                  href="/patient/payments"
                  className="mt-4 inline-flex min-h-11 items-center gap-2 rounded-sm font-bold text-green-800 hover:text-action"
                >
                  View payment history <ArrowRight aria-hidden size={18} />
                </Link>
              </Card>
              <Alert tone="success" title="Your information is protected">
                Only people with a role in your care can see your clinical information, and doctors need your permission to
                read your records.
              </Alert>
            </div>
          </div>
          <div className="order-5 min-w-0 lg:order-none">
            <UpdatesCard
              resource={notifications.resource}
              unread={unread}
              onRetry={notifications.reload}
              onRead={(updated) =>
                notifications.resource.state === "ready" &&
                notifications.replace({
                  ...notifications.resource.data,
                  content: notifications.resource.data.content.map((item) => (item.id === updated.id ? updated : item)),
                })
              }
            />
          </div>
        </div>
      </div>
    </>
  );
}

function NextAppointmentCard({
  resource,
  next,
  onRetry,
}: {
  resource: Resource<unknown>;
  next: ReturnType<typeof nextAppointment>;
  onRetry: () => void;
}) {
  if (resource.state === "loading") {
    return (
      <Card aria-busy>
        <span role="status" className="sr-only">
          Loading appointments
        </span>
        <Skeleton className="h-4 w-40" />
        <Skeleton className="mt-3 h-7 w-56" />
        <Skeleton className="mt-6 h-16 w-full" />
        <Skeleton className="mt-6 h-11 w-48" />
      </Card>
    );
  }
  if (resource.state === "error") {
    return (
      <Card>
        <CardHeader eyebrow="Next appointment" title="Appointments unavailable" />
        <StateMessage
          kind="error"
          layout="inline"
          className="mt-4"
          title="Your appointments could not be loaded."
          action={<RetryButton onClick={onRetry} />}
        />
      </Card>
    );
  }
  if (!next) {
    return (
      <Card>
        <CardHeader eyebrow="Next appointment" title="No upcoming appointments" />
        <p className="mt-3 text-data text-text-secondary">
          Find an approved doctor and request one of their open times. The doctor confirms each request.
        </p>
        <div className="mt-5 flex flex-wrap gap-3">
          <ButtonLink href="/patient/find-a-doctor">Find a doctor</ButtonLink>
          <ButtonLink href="/patient/appointments" variant="secondary">
            Past appointments
          </ButtonLink>
        </div>
      </Card>
    );
  }
  return (
    <AppointmentSummary
      when={formatWhen(next.startsAt)}
      status={next.status}
      doctorName={next.providerName}
      specialty={next.providerSpecialty || "Approved provider"}
      mode={consultationModeLabel(next.consultationMode)}
      modeIcon={next.consultationMode === "IN_PERSON" ? MapPin : MessageSquareText}
      details={[
        { term: "Date", detail: formatDate(next.startsAt) },
        { term: "Duration", detail: formatDuration(next.startsAt, next.endsAt) },
        { term: "Time zone", detail: CARE_TIME_ZONE_LABEL },
      ]}
      actions={
        <ButtonLink href={`/patient/appointments?appointment=${encodeURIComponent(next.id)}`} variant="secondary">
          View details
        </ButtonLink>
      }
    />
  );
}

function RequestsCard({
  resource,
  onRetry,
}: {
  resource: Resource<{ lab: ServiceRequest[]; homeCare: ServiceRequest[] }>;
  onRetry: () => void;
}) {
  const open = (list: ServiceRequest[]) => list.filter((item) => item.status === "OPEN" || item.status === "IN_PROGRESS");
  return (
    <Card>
      <CardHeader eyebrow="Requests" title="Laboratory and home care" />
      <div className="mt-4 border-t border-border">
        {resource.state === "loading" ? (
          <ListSkeleton label="Loading requests" rows={2} />
        ) : resource.state === "error" ? (
          <StateMessage kind="error" layout="inline" className="mt-4" title="Your requests could not be loaded." action={<RetryButton onClick={onRetry} />} />
        ) : (
          <ActivityList label="Request summary">
            <ActivityRow
              icon={ClipboardList}
              title="Laboratory requests"
              meta={`${plural(open(resource.data.lab).length, "open request")} · ${resource.data.lab.length} in total`}
              trailing={latestStatus(resource.data.lab)}
              href="/patient/records?tab=laboratory"
            />
            <ActivityRow
              icon={ClipboardList}
              title="Home-care requests"
              meta={`${plural(open(resource.data.homeCare).length, "open request")} · ${resource.data.homeCare.length} in total`}
              trailing={latestStatus(resource.data.homeCare)}
              href="/patient/appointments?tab=home-care"
            />
          </ActivityList>
        )}
      </div>
    </Card>
  );
}

function latestStatus(list: ServiceRequest[]) {
  const latest = [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
  return latest ? <ServiceRequestStatusBadge status={latest.status} /> : undefined;
}

function UpdatesCard({
  resource,
  unread,
  onRetry,
  onRead,
}: {
  resource: Resource<{ content: Notification[] }>;
  unread: number;
  onRetry: () => void;
  onRead: (notification: Notification) => void;
}) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState("");

  async function markRead(id: string) {
    setBusyId(id);
    setError("");
    try {
      onRead(await patientApi.markNotificationRead(id));
    } catch (failure) {
      setError(actionError(failure, "The update could not be marked as read."));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card>
      <CardHeader
        eyebrow="Updates"
        title="Notifications"
        action={unread ? <Badge tone="info">{unread} unread</Badge> : undefined}
      />
      <div className="mt-4 border-t border-border">
        {error ? <StateMessage kind="error" layout="inline" className="mt-4" title={error} /> : null}
        {resource.state === "loading" ? (
          <ListSkeleton label="Loading notifications" rows={2} />
        ) : resource.state === "error" ? (
          <StateMessage kind="error" layout="inline" className="mt-4" title="Notifications could not be loaded." action={<RetryButton onClick={onRetry} />} />
        ) : resource.data.content.length ? (
          <ActivityList label="Latest notifications">
            {resource.data.content.map((item) => (
              <ActivityRow
                key={item.id}
                icon={Bell}
                tileTone={item.readAt ? "neutral" : "info"}
                title={
                  notificationHref(item) ? (
                    <Link href={notificationHref(item)!} className="inline-flex min-h-10 items-center rounded-sm underline decoration-1 underline-offset-2 hover:text-action">
                      {item.title}
                    </Link>
                  ) : (
                    item.title
                  )
                }
                meta={`${formatShortDate(item.createdAt)}${item.readAt ? " · Read" : " · Unread"}`}
                trailing={
                  item.readAt ? undefined : (
                    <Button variant="tertiary" size="sm" loading={busyId === item.id} onClick={() => void markRead(item.id)}>
                      Mark as read
                    </Button>
                  )
                }
              />
            ))}
          </ActivityList>
        ) : (
          <StateMessage kind="empty" layout="inline" className="mt-4" title="No notifications yet." />
        )}
      </div>
    </Card>
  );
}

/** Where a notification's resource lives in the patient workspace; titles stay generic (no clinical content). */
export function notificationHref(notification: Notification): string | undefined {
  const id = encodeURIComponent(notification.resourceId);
  if (notification.resourceType === "appointment") return `/patient/appointments?appointment=${id}`;
  if (notification.resourceType === "consultation") return `/patient/appointments/consultation?consultation=${id}`;
  return undefined;
}

function ViewAll({ href, label }: { href: string; label: string }) {
  return (
    <Link href={href} aria-label={label} className="inline-flex min-h-11 items-center gap-2 rounded-sm font-bold text-green-800 hover:text-action">
      View all <ArrowRight aria-hidden size={18} />
    </Link>
  );
}

function RetryButton({ onClick }: { onClick: () => void }) {
  return (
    <Button variant="secondary" size="sm" onClick={onClick}>
      Try again
    </Button>
  );
}

function ListSkeleton({ label, rows = 3 }: { label: string; rows?: number }) {
  return (
    <div aria-busy className="flex flex-col gap-4 py-4">
      <span role="status" className="sr-only">
        {label}
      </span>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}
