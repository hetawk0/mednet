"use client";

import { Activity, Pill } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { TextInput } from "@/components/ui/inputs";
import { Pagination } from "@/components/ui/pagination";
import { StateMessage } from "@/components/ui/state-message";
import { CARE_TIME_ZONE, careDay } from "@/features/find-care/format";
import { formatDate, formatWhen } from "./format";
import { LIMITS, patientApi, type DoseLog, type Medication, type Vital } from "./patient-api";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { usePageFor } from "@/features/workspace/paging";
import { CountBadge } from "./view-frame";

/**
 * Patient-entered medication reminders and vital readings (`MedicationController`,
 * `PatientVitalController`). These are the patient's own notes: MedNet does not prescribe,
 * and readings are shown exactly as entered with no ranges or interpretation.
 */

type Notice = { tone: "success" | "critical"; text: string } | null;

/** Reminder schedules use the care time zone; the API accepts any IANA zone. */
const REMINDER_TIME_ZONE = CARE_TIME_ZONE;
const MEDICATION_PAGE_SIZE = 25;

export function MedicationPanel() {
  const [page, setPage] = usePageFor("medications");
  const { resource, reload, replace } = useResource(
    (signal) => patientApi.medicationsPage(page, MEDICATION_PAGE_SIZE, signal),
    String(page),
  );
  const [stopping, setStopping] = useState<Medication | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const medications = resource.state === "ready" ? resource.data.content : [];

  function upsert(updated: Medication) {
    if (resource.state !== "ready") return reload();
    const exists = resource.data.content.some((item) => item.id === updated.id);
    if (!exists && page > 0) {
      setPage(0);
      return;
    }
    replace({
      ...resource.data,
      totalElements: resource.data.totalElements + (exists ? 0 : 1),
      content: exists
        ? resource.data.content.map((item) => (item.id === updated.id ? updated : item))
        : [updated, ...resource.data.content].slice(0, MEDICATION_PAGE_SIZE),
    });
  }

  async function stop() {
    if (!stopping) return;
    setBusyId(stopping.id);
    try {
      upsert(await patientApi.stopMedication(stopping.id));
      setNotice({ tone: "success", text: `Reminders for ${stopping.medicationName} stopped.` });
    } catch (error) {
      setNotice({ tone: "critical", text: actionError(error, "The reminder could not be stopped.") });
    } finally {
      setBusyId(null);
      setStopping(null);
    }
  }

  async function logDose(medication: Medication) {
    setBusyId(medication.id);
    setNotice(null);
    try {
      await patientApi.logDose(medication.id);
      setNotice({ tone: "success", text: `Today’s dose of ${medication.medicationName} recorded.` });
      setHistoryVersion((version) => version + 1);
      reload();
    } catch (error) {
      setNotice({ tone: "critical", text: actionError(error, "The dose could not be recorded.") });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          eyebrow="Your notes"
          title="Medication reminders"
          action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun="reminder" /> : undefined}
        />
        <p className="mt-1.5 text-data text-text-secondary">
          Reminders you set for medicines you already take. They are not prescriptions; follow your doctor’s instructions.
        </p>
        {notice ? (
          <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} className="mt-4" />
        ) : null}
        <div className="mt-4 border-t border-border">
          {resource.state === "loading" ? (
            <LoadingRows label="Loading medication reminders" />
          ) : resource.state === "error" ? (
            <ErrorRow title="Your medication reminders could not be loaded." onRetry={reload} status={resource.status} />
          ) : medications.length ? (
            <ul aria-label="Medication reminders" className="divide-y divide-line-soft">
              {medications.map((medication) => (
                <li key={medication.id} className="flex flex-wrap items-center gap-3 py-4">
                  <span aria-hidden className="inline-flex size-10 shrink-0 items-center justify-center rounded-sm bg-surface-soft text-green-800">
                    <Pill size={20} strokeWidth={1.8} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink [overflow-wrap:anywhere]">
                      {medication.medicationName} · {medication.dose}
                    </p>
                    <p className="text-caption text-text-secondary">
                      Daily at {medication.reminderTime.slice(0, 5)} ({medication.timeZone}) · from {formatDate(medication.startDate)}
                      {medication.endDate ? ` to ${formatDate(medication.endDate)}` : ""}
                      {medication.status === "ACTIVE" && medication.nextReminderAt ? ` · Next ${formatWhen(medication.nextReminderAt)}` : ""}
                    </p>
                  </div>
                  <Badge tone={medication.status === "ACTIVE" ? "success" : "neutral"}>
                    {medication.status === "ACTIVE" ? "Active" : "Stopped"}
                  </Badge>
                  {medication.status === "ACTIVE" ? (
                    <div className="flex gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        loading={busyId === medication.id && !stopping}
                        aria-label={`Record today’s dose of ${medication.medicationName}`}
                        onClick={() => void logDose(medication)}
                      >
                        Record dose
                      </Button>
                      <Button
                        variant="tertiary"
                        size="sm"
                        aria-label={`Stop reminders for ${medication.medicationName}`}
                        onClick={() => {
                          setNotice(null);
                          setStopping(medication);
                        }}
                      >
                        Stop
                      </Button>
                    </div>
                  ) : null}
                  <Button
                    variant="tertiary"
                    size="sm"
                    aria-expanded={historyId === medication.id}
                    aria-controls={`dose-history-${medication.id}`}
                    onClick={() => setHistoryId(historyId === medication.id ? null : medication.id)}
                  >
                    {historyId === medication.id ? "Hide dose history" : "Dose history"}
                  </Button>
                  {historyId === medication.id ? (
                    <div id={`dose-history-${medication.id}`} className="basis-full">
                      <DoseHistory scheduleId={medication.id} refreshKey={historyVersion} />
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : (
            <StateMessage kind="empty" layout="inline" className="mt-4" title="No medication reminders yet." />
          )}
        </div>
        {resource.state === "ready" ? (
          <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label="Medication reminder pages" />
        ) : null}
      </Card>

      <MedicationForm
        onCreated={(created) => {
          upsert(created);
          setNotice({ tone: "success", text: `Reminder for ${created.medicationName} added.` });
        }}
      />

      <ConfirmDialog
        open={stopping !== null}
        onClose={() => busyId === null && setStopping(null)}
        onConfirm={() => void stop()}
        loading={busyId !== null}
        tone="destructive"
        title="Stop these reminders?"
        description={stopping ? `${stopping.medicationName} · ${stopping.dose}. You can add a new reminder later.` : ""}
        confirmLabel="Stop reminders"
      />
    </div>
  );
}

function DoseHistory({ scheduleId, refreshKey }: { scheduleId: string; refreshKey: number }) {
  const pageSize = 20;
  const [page, setPage] = usePageFor(`${scheduleId}-${refreshKey}`);
  const { resource, reload } = useResource(
    (signal) => patientApi.doseLogs(scheduleId, page, pageSize, signal),
    `${scheduleId}-${page}`,
  );
  const logs: DoseLog[] = resource.state === "ready" ? resource.data.content : [];

  return (
    <section aria-label="Dose history" className="rounded-sm border border-border bg-surface-soft p-4">
      <h3 className="font-semibold text-ink">
        Dose history{resource.state === "ready" ? ` · ${resource.data.totalElements} recorded doses` : ""}
      </h3>
      {resource.state === "loading" ? (
        <LoadingRows label="Loading dose history" rows={2} />
      ) : resource.state === "error" ? (
        <ErrorRow title="Dose history could not be loaded." onRetry={reload} status={resource.status} />
      ) : logs.length ? (
        <ul className="mt-2 divide-y divide-line-soft">
          {logs.map((log) => (
            <li key={log.id} className="flex flex-wrap justify-between gap-2 py-2 text-caption">
              <span>{formatDate(log.doseDate)}</span>
              <time dateTime={log.takenAt}>Recorded {formatWhen(log.takenAt)}</time>
            </li>
          ))}
        </ul>
      ) : (
        <StateMessage kind="empty" layout="inline" className="mt-2" title="No doses recorded yet." />
      )}
      {resource.state === "ready" ? (
        <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label="Dose history pages" />
      ) : null}
    </section>
  );
}

function MedicationForm({ onCreated }: { onCreated: (medication: Medication) => void }) {
  const today = careDay(new Date());
  const empty = { medicationName: "", dose: "", reminderTime: "08:00", startDate: today, endDate: "" };
  const [values, setValues] = useState(empty);
  const [errors, setErrors] = useState<Partial<Record<keyof typeof empty, string>>>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const set = (key: keyof typeof empty) => (event: { target: { value: string } }) => setValues({ ...values, [key]: event.target.value });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: typeof errors = {
      medicationName: values.medicationName.trim() ? undefined : "Enter the medicine’s name.",
      dose: values.dose.trim() ? undefined : "Enter the dose, for example “1 tablet”.",
      reminderTime: values.reminderTime ? undefined : "Choose a reminder time.",
      startDate: values.startDate ? undefined : "Choose a start date.",
      endDate: values.endDate && values.endDate < values.startDate ? "The end date must be on or after the start date." : undefined,
    };
    setErrors(next);
    setFailure("");
    if (Object.values(next).some(Boolean)) {
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setBusy(true);
    try {
      const created = await patientApi.createMedication({
        medicationName: values.medicationName.trim(),
        dose: values.dose.trim(),
        reminderTime: values.reminderTime,
        timeZone: REMINDER_TIME_ZONE,
        startDate: values.startDate,
        endDate: values.endDate || null,
      });
      setValues(empty);
      onCreated(created);
    } catch (error) {
      setFailure(actionError(error, "The reminder could not be added."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Add a medication reminder" titleAs="h3" />
      <form noValidate onSubmit={submit} className="mt-5 grid gap-4 md:grid-cols-2">
        <TextInput label="Medicine" required maxLength={LIMITS.medicationName} value={values.medicationName} error={errors.medicationName} onChange={set("medicationName")} />
        <TextInput label="Dose" required maxLength={LIMITS.dose} value={values.dose} error={errors.dose} onChange={set("dose")} />
        <TextInput
          label="Reminder time"
          type="time"
          required
          value={values.reminderTime}
          error={errors.reminderTime}
          description="Liberia time (GMT)"
          onChange={set("reminderTime")}
        />
        <TextInput label="Start date" type="date" required value={values.startDate} error={errors.startDate} onChange={set("startDate")} />
        <TextInput
          label="End date"
          type="date"
          min={values.startDate}
          value={values.endDate}
          error={errors.endDate}
          description="Optional"
          onChange={set("endDate")}
        />
        {failure ? <Alert tone="critical" live="assertive" title={failure} className="md:col-span-2" /> : null}
        <div className="md:col-span-2">
          <Button type="submit" loading={busy}>
            Add reminder
          </Button>
        </div>
      </form>
    </Card>
  );
}

const VITAL_PAGE_SIZE = 20;

export function VitalPanel() {
  const [page, setPage] = usePageFor("patient-vitals");
  const { resource, reload, replace } = useResource((signal) => patientApi.vitals(page, VITAL_PAGE_SIZE, signal), String(page));
  const [notice, setNotice] = useState<Notice>(null);
  const vitals: Vital[] = resource.state === "ready" ? resource.data.content : [];

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          eyebrow="Your notes"
          title="Vital readings"
          action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun="reading" /> : undefined}
        />
        <p className="mt-1.5 text-data text-text-secondary">
          Readings you record yourself, shown exactly as entered. Doctors with your permission can see them.
        </p>
        {notice ? (
          <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} className="mt-4" />
        ) : null}
        <div className="mt-4 border-t border-border">
          {resource.state === "loading" ? (
            <LoadingRows label="Loading vital readings" />
          ) : resource.state === "error" ? (
            <ErrorRow title="Your vital readings could not be loaded." onRetry={reload} status={resource.status} />
          ) : vitals.length ? (
            <ul aria-label="Vital readings" className="divide-y divide-line-soft">
              {vitals.map((vital) => (
                <li key={vital.id} className="flex flex-wrap items-center gap-3 py-4">
                  <span aria-hidden className="inline-flex size-10 shrink-0 items-center justify-center rounded-sm bg-surface-soft text-green-800">
                    <Activity size={20} strokeWidth={1.8} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-ink [overflow-wrap:anywhere]">{vital.metric}</p>
                    <p className="text-caption text-text-secondary">{formatWhen(vital.recordedAt)} · Recorded by you</p>
                  </div>
                  <p className="text-data font-semibold text-ink tabular-nums">
                    {vital.value} {vital.unit}
                  </p>
                </li>
              ))}
            </ul>
          ) : (
            <StateMessage kind="empty" layout="inline" className="mt-4" title="No readings yet." />
          )}
        </div>
        {resource.state === "ready" ? (
          <Pagination page={resource.data.number + 1} totalPages={resource.data.totalPages} onPageChange={(next) => setPage(next - 1)} label="Vital reading pages" />
        ) : null}
      </Card>

      <VitalForm
        onCreated={(created) => {
          if (resource.state === "ready") {
            if (page > 0) setPage(0);
            else {
              replace({
                ...resource.data,
                totalElements: resource.data.totalElements + 1,
                content: [created, ...resource.data.content]
                  .sort((a, b) => b.recordedAt.localeCompare(a.recordedAt))
                  .slice(0, VITAL_PAGE_SIZE),
              });
            }
          } else reload();
          setNotice({ tone: "success", text: `${created.metric} reading saved.` });
        }}
      />
    </div>
  );
}

/** `datetime-local` value for "now" in the viewer's own clock (the reading is taken where they are). */
function localNow(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function VitalForm({ onCreated }: { onCreated: (vital: Vital) => void }) {
  const [values, setValues] = useState(() => ({ metric: "", value: "", unit: "", recordedAt: localNow() }));
  const [errors, setErrors] = useState<Partial<Record<keyof typeof values, string>>>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const set = (key: keyof typeof values) => (event: { target: { value: string } }) => setValues({ ...values, [key]: event.target.value });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const number = Number(values.value);
    const recordedAt = values.recordedAt ? new Date(values.recordedAt) : null;
    const next: typeof errors = {
      metric: values.metric.trim() ? undefined : "Enter what you measured, for example “Blood pressure (systolic)”.",
      value:
        values.value.trim() && Number.isFinite(number) && number >= 0 && /^\d{1,9}(\.\d{1,3})?$/.test(values.value.trim())
          ? undefined
          : "Enter a number of zero or more, with up to 3 decimal places.",
      unit: values.unit.trim() ? undefined : "Enter the unit, for example “mmHg”.",
      recordedAt: !recordedAt || Number.isNaN(recordedAt.getTime())
        ? "Choose when you took the reading."
        : recordedAt.getTime() > Date.now()
          ? "A reading cannot be in the future."
          : undefined,
    };
    setErrors(next);
    setFailure("");
    if (Object.values(next).some(Boolean)) {
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setBusy(true);
    try {
      const created = await patientApi.recordVital({
        metric: values.metric.trim(),
        value: number,
        unit: values.unit.trim(),
        recordedAt: recordedAt!.toISOString(),
      });
      setValues({ metric: "", value: "", unit: "", recordedAt: localNow() });
      onCreated(created);
    } catch (error) {
      setFailure(actionError(error, "The reading could not be saved."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Record a reading" titleAs="h3" />
      <form noValidate onSubmit={submit} className="mt-5 grid gap-4 md:grid-cols-2">
        <TextInput label="Measurement" required maxLength={LIMITS.vitalMetric} value={values.metric} error={errors.metric} onChange={set("metric")} />
        <TextInput label="When" type="datetime-local" required max={localNow()} value={values.recordedAt} error={errors.recordedAt} onChange={set("recordedAt")} />
        <TextInput label="Value" inputMode="decimal" required value={values.value} error={errors.value} onChange={set("value")} />
        <TextInput label="Unit" required maxLength={LIMITS.vitalUnit} value={values.unit} error={errors.unit} onChange={set("unit")} />
        <Alert tone="warning" title="Not for emergencies" className="md:col-span-2">
          MedNet does not monitor readings or raise alerts, and it does not replace emergency services.
        </Alert>
        {failure ? <Alert tone="critical" live="assertive" title={failure} className="md:col-span-2" /> : null}
        <div className="md:col-span-2">
          <Button type="submit" loading={busy}>
            Save reading
          </Button>
        </div>
      </form>
    </Card>
  );
}
