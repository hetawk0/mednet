"use client";

import { Activity, FileText, FlaskConical } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AppointmentStatusBadge, LabResultStatusBadge } from "@/components/healthcare/status";
import { Alert } from "@/components/ui/alert";
import { Avatar } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/dialog";
import { SelectField, TextArea, TextInput } from "@/components/ui/inputs";
import { Pagination } from "@/components/ui/pagination";
import { StateMessage } from "@/components/ui/state-message";
import { Tabs } from "@/components/ui/tabs";
import { consultationModeLabel } from "@/features/find-care/format";
import { formatDate, formatShortDate, formatWhen, recordCategoryLabel } from "@/features/patient/format";
import type { Appointment, ClinicalRecord, LabResult } from "@/features/patient/patient-api";
import { CountBadge, PrivacyCard, ViewBand, ViewColumns } from "@/features/patient/view-frame";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { LIST_PAGE_SIZE } from "@/features/workspace/paging";
import { PROVIDER_LIMITS, RECORD_CATEGORIES, providerApi, type ProviderPatientSummary, type RecordCategory } from "./provider-api";
import type { PatientTab } from "./tabs";

export function ProviderPatients() {
  const [page, setPage] = useState(0);
  const { resource, reload } = useResource((signal) => providerApi.patients(page, LIST_PAGE_SIZE, signal), String(page));
  const patients = resource.state === "ready" ? resource.data.content : [];
  return (
    <>
      <ViewBand eyebrow="Doctor workspace" title="Patients" description="Patients with an eligible care relationship with you." />
      <ViewColumns
        aside={
          <PrivacyCard>
            You can open a patient’s records only while an eligible care relationship is active and they have allowed you
            access. MedNet checks this on every request.
          </PrivacyCard>
        }
      >
        <Card>
          <CardHeader eyebrow="Current view" title="Your patients" action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun="patient" /> : undefined} />
          <div className="mt-4 border-t border-border">
            {resource.state === "loading" ? (
              <LoadingRows label="Loading patients" />
            ) : resource.state === "error" ? (
              <ErrorRow title="Your patients could not be loaded." onRetry={reload} status={resource.status} />
            ) : patients.length ? (
              <ul aria-label="Your patients" className="divide-y divide-line-soft">
                {patients.map((patient) => (
                  <li key={patient.patientAccountId} className="flex flex-wrap items-center gap-3 py-4">
                    <Avatar name={patient.name} />
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-ink [overflow-wrap:anywhere]">{patient.name}</p>
                      <p className="text-caption text-text-secondary">
                        Eligible care relationship with you
                      </p>
                    </div>
                    <Badge tone="success">Care relationship</Badge>
                    <ButtonLink href={`/provider/patients/${encodeURIComponent(patient.patientAccountId)}`} size="sm" variant="secondary" aria-label={`Open ${patient.name}`}>
                      Open patient
                    </ButtonLink>
                  </li>
                ))}
              </ul>
            ) : (
              <StateMessage kind="empty" className="mt-4" title="No patients yet" description="Patients appear here after a confirmed, completed or rescheduling appointment." />
            )}
          </div>
          {resource.state === "ready" && resource.data.totalPages > 1 ? (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
              <p className="text-caption text-text-secondary" aria-live="polite">
                Showing patients {page * resource.data.size + 1}–{page * resource.data.size + patients.length} of {resource.data.totalElements}.
              </p>
              <Pagination
                page={page + 1}
                totalPages={resource.data.totalPages}
                onPageChange={(nextPage) => setPage(nextPage - 1)}
                label="Patient directory pages"
              />
            </div>
          ) : null}
        </Card>
      </ViewColumns>
    </>
  );
}


/**
 * One patient. Everything clinical is fetched with the patient's opaque account id and authorised
 * by the API (care relationship + consent); a 403 shows what is missing instead of the data.
 */
export function ProviderPatientDetail({ patientAccountId, tab }: { patientAccountId: string; tab: PatientTab }) {
  const router = useRouter();
  const patientResource = useResource((signal) => providerApi.patient(patientAccountId, signal), patientAccountId);
  const base = `/provider/patients/${encodeURIComponent(patientAccountId)}`;

  if (patientResource.resource.state === "loading") return <LoadingRows label="Loading the patient" />;
  if (patientResource.resource.state === "error") {
    if (patientResource.resource.status === 404 || patientResource.resource.status === 403) {
      return (
        <StateMessage
          kind="denied"
          title="Patient not found"
          description="You can open only patients with an eligible care relationship with you."
          action={<ButtonLink href="/provider/patients" variant="secondary">Back to patients</ButtonLink>}
        />
      );
    }
    return <ErrorRow title="The patient could not be loaded." onRetry={patientResource.reload} status={patientResource.resource.status} />;
  }
  const patient: ProviderPatientSummary = patientResource.resource.data;

  return (
    <>
      <Link href="/provider/patients" className="inline-flex min-h-11 items-center gap-2 self-start rounded-sm font-bold text-green-800 hover:text-action">
        ← All patients
      </Link>
      <Card>
        <div className="flex flex-wrap items-center gap-4">
          <Avatar name={patient.name} size="lg" />
          <div className="min-w-0 flex-1">
            <h2 className="text-title text-ink [overflow-wrap:anywhere]">{patient.name}</h2>
            <p className="text-caption text-text-secondary">Eligible care relationship with you</p>
          </div>
          <Badge tone="success">Care relationship</Badge>
        </div>
      </Card>
      <ViewColumns
        aside={
          <PrivacyCard>
            Records, vital readings and results are shown only while the patient allows you access. Patients can withdraw it at
            any time; the page then shows that access is not available.
          </PrivacyCard>
        }
      >
        <Tabs
          label="Patient information"
          value={tab}
          onChange={(id) => router.replace(id === "records" ? base : `${base}?tab=${id}`, { scroll: false })}
          items={[
            { id: "records", label: "Records", content: tab === "records" ? <PatientRecords patientAccountId={patientAccountId} /> : null },
            { id: "results", label: "Results to review", content: tab === "results" ? <ResultsToReview patientAccountId={patientAccountId} /> : null },
            { id: "vitals", label: "Vitals", content: tab === "vitals" ? <PatientVitals patientAccountId={patientAccountId} /> : null },
            {
              id: "appointments",
              label: "Appointments",
              content:
                tab === "appointments" ? (
                    <PatientAppointments patientAccountId={patientAccountId} />
                  ) : null,
            },
          ]}
        />
      </ViewColumns>
    </>
  );
}

function PatientAppointments({ patientAccountId }: { patientAccountId: string }) {
  const [page, setPage] = useState(0);
  const { resource, reload } = useResource(
    (signal) => providerApi.patientAppointments(patientAccountId, page, LIST_PAGE_SIZE, signal),
    `${patientAccountId}:${page}`,
  );

  return (
    <Card>
      <CardHeader
        eyebrow="With you"
        title="Appointments"
        action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} /> : undefined}
      />
      {resource.state === "loading" ? (
        <LoadingRows label="Loading appointments" />
      ) : resource.state === "error" ? (
        <ErrorRow title="Appointments could not be loaded." onRetry={reload} status={resource.status} />
      ) : resource.data.content.length ? (
        <ul aria-label="Patient appointments" className="mt-4 divide-y divide-line-soft border-t border-border">
          {resource.data.content.map((appointment: Appointment) => (
            <li key={appointment.id} className="flex flex-wrap items-center gap-3 py-3">
              <span className="min-w-0 flex-1 text-data text-ink">
                {formatWhen(appointment.startsAt)} · {consultationModeLabel(appointment.consultationMode)}
              </span>
              <AppointmentStatusBadge status={appointment.status} />
              <ButtonLink href={`/provider/appointments?tab=history&appointment=${encodeURIComponent(appointment.id)}`} size="sm" variant="tertiary">
                Details
              </ButtonLink>
            </li>
          ))}
        </ul>
      ) : (
        <StateMessage kind="empty" layout="inline" className="mt-4" title="No appointments with this patient." />
      )}
      {resource.state === "ready" && resource.data.totalPages > 1 ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
          <p className="text-caption text-text-secondary" aria-live="polite">
            Showing appointments {page * resource.data.size + 1}–{page * resource.data.size + resource.data.content.length} of {resource.data.totalElements}.
          </p>
          <Pagination
            page={page + 1}
            totalPages={resource.data.totalPages}
            onPageChange={(nextPage) => setPage(nextPage - 1)}
            label="Patient appointment pages"
          />
        </div>
      ) : null}
    </Card>
  );
}

/** Explains a 403 from the record APIs using the API's own reason. */
function NoAccess({ message }: { message: string }) {
  const consent = /consent/i.test(message);
  return (
    <StateMessage
      kind="denied"
      className="mt-4"
      title={consent ? "The patient has not allowed you to see their records" : "No current care relationship"}
      description={
        consent
          ? "Patients choose which doctors can see their records from their own workspace. Ask the patient during your appointment."
          : "Records open only while you have a confirmed or completed appointment with this patient."
      }
    />
  );
}

const RECORD_PAGE = 10;

function PatientRecords({ patientAccountId }: { patientAccountId: string }) {
  const [page, setPage] = useState(1);
  const [amending, setAmending] = useState<ClinicalRecord | null>(null);
  // Kept here: the form remounts after saving a correction, which would drop its own message.
  const [saved, setSaved] = useState("");
  const { resource, reload } = useResource((signal) => providerApi.records(patientAccountId, page - 1, RECORD_PAGE, signal), `${patientAccountId}|${page}`);
  const forbidden = resource.state === "error" && resource.status === 403;
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader eyebrow="Clinical record" title="Records" action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun="record" /> : undefined} />
        {resource.state === "loading" ? (
          <LoadingRows label="Loading records" />
        ) : resource.state === "error" ? (
          forbidden ? <NoAccess message={resource.message} /> : <ErrorRow title="Records could not be loaded." onRetry={reload} status={resource.status} />
        ) : resource.data.content.length ? (
          <ul aria-label="Records" className="mt-4 divide-y divide-line-soft border-t border-border">
            {resource.data.content.map((record) => (
              <li key={record.id} className="py-4">
                <details className="group">
                  <summary className="flex min-h-11 cursor-pointer list-none items-center gap-3 rounded-sm [&::-webkit-details-marker]:hidden">
                    <span aria-hidden className="inline-flex size-10 shrink-0 items-center justify-center rounded-sm bg-surface-soft text-green-800">
                      <FileText size={20} strokeWidth={1.8} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-bold text-ink [overflow-wrap:anywhere]">{record.title}</span>
                      <span className="block text-caption text-text-secondary">
                        {recordCategoryLabel(record.category)} · {formatDate(record.effectiveAt)} · {record.authorName}
                      </span>
                    </span>
                    {record.amendsRecordId ? <Badge tone="info">Correction</Badge> : null}
                    <span className="text-caption font-semibold text-green-800 group-open:hidden">Show</span>
                    <span className="hidden text-caption font-semibold text-green-800 group-open:inline">Hide</span>
                  </summary>
                  <div className="mt-3 ml-13 flex flex-col gap-3">
                    {record.clinicalCode ? <p className="text-caption text-text-secondary">Code: {record.clinicalCode}</p> : null}
                    <p className="text-data whitespace-pre-line text-ink [overflow-wrap:anywhere]">{record.summary}</p>
                    <div>
                      <Button size="sm" variant="tertiary" onClick={() => setAmending(record)}>
                        Add a correction
                      </Button>
                    </div>
                  </div>
                </details>
              </li>
            ))}
          </ul>
        ) : (
          <StateMessage kind="empty" layout="inline" className="mt-4" title="No records yet." />
        )}
        {resource.state === "ready" && resource.data.totalPages > 1 ? (
          <div className="mt-5">
            <Pagination page={page} totalPages={resource.data.totalPages} onPageChange={setPage} label="Record pages" />
          </div>
        ) : null}
      </Card>
      {saved ? <Alert tone="success" live="polite" title={saved} /> : null}
      {!forbidden ? (
        <RecordForm
          key={amending?.id ?? "new"}
          patientAccountId={patientAccountId}
          amends={amending}
          onCancelAmend={() => setAmending(null)}
          onEdit={() => setSaved("")}
          onSaved={(message) => {
            setSaved(message);
            setAmending(null);
            setPage(1);
            reload();
          }}
        />
      ) : null}
    </div>
  );
}

/** `datetime-local` value for now in Liberia time (GMT), which equals UTC. */
function liberiaNow(): string {
  return new Date().toISOString().slice(0, 16);
}

function RecordForm({
  patientAccountId,
  amends,
  onCancelAmend,
  onEdit,
  onSaved,
}: {
  patientAccountId: string;
  amends: ClinicalRecord | null;
  onCancelAmend: () => void;
  onEdit: () => void;
  onSaved: (message: string) => void;
}) {
  const [values, setValues] = useState({
    category: (amends?.category as RecordCategory) ?? "ENCOUNTER_SUMMARY",
    title: amends ? `Correction: ${amends.title}`.slice(0, PROVIDER_LIMITS.recordTitle) : "",
    clinicalCode: amends?.clinicalCode ?? "",
    summary: "",
    effectiveAt: liberiaNow(),
  });
  const [errors, setErrors] = useState<Partial<Record<keyof typeof values, string>>>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const set = (key: keyof typeof values) => (event: { target: { value: string } }) => {
    onEdit();
    setValues({ ...values, [key]: event.target.value });
  };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const effective = values.effectiveAt ? new Date(`${values.effectiveAt}:00Z`) : null;
    const next = {
      title: values.title.trim() ? undefined : "Enter a title.",
      summary: values.summary.trim() ? undefined : "Enter the clinical note.",
      effectiveAt: !effective || Number.isNaN(effective.getTime()) ? "Choose when this applies." : undefined,
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
      await providerApi.addRecord(patientAccountId, {
        category: values.category,
        title: values.title.trim(),
        clinicalCode: values.clinicalCode.trim() || undefined,
        summary: values.summary.trim(),
        effectiveAt: effective!.toISOString(),
        amendsRecordId: amends?.id,
      });
      setValues({ ...values, title: "", clinicalCode: "", summary: "" });
      onSaved(amends ? "Correction added. The original record is kept." : "Record added to the patient’s file.");
    } catch (error) {
      setFailure(actionError(error, "The record could not be saved."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader
        title={amends ? "Add a correction" : "Document a consultation"}
        titleAs="h3"
        action={amends ? <Button size="sm" variant="tertiary" onClick={onCancelAmend}>Cancel correction</Button> : undefined}
      />
      <p className="mt-1.5 text-data text-text-secondary">
        {amends
          ? "Records cannot be edited or deleted. A correction is added as a new record linked to the original."
          : "The patient can read every record you add. Records cannot be edited or deleted later."}
      </p>
      {failure ? <Alert tone="critical" live="assertive" title={failure} className="mt-4" /> : null}
      <form noValidate onSubmit={submit} className="mt-5 grid grid-cols-[minmax(0,1fr)] gap-4 md:grid-cols-2">
        <SelectField
          label="Category"
          value={values.category}
          onChange={set("category")}
          options={RECORD_CATEGORIES.map((category) => ({ value: category, label: recordCategoryLabel(category) }))}
        />
        <TextInput label="Applies from" type="datetime-local" required value={values.effectiveAt} error={errors.effectiveAt} description="Liberia time (GMT)" onChange={set("effectiveAt")} />
        <TextInput label="Title" required maxLength={PROVIDER_LIMITS.recordTitle} value={values.title} error={errors.title} onChange={set("title")} />
        <TextInput label="Clinical code" maxLength={PROVIDER_LIMITS.clinicalCode} value={values.clinicalCode} description="Optional" onChange={set("clinicalCode")} />
        <TextArea
          label="Clinical note"
          required
          rows={6}
          maxLength={PROVIDER_LIMITS.recordSummary}
          value={values.summary}
          error={errors.summary}
          fieldClassName="md:col-span-2"
          onChange={set("summary")}
        />
        <div className="md:col-span-2">
          <Button type="submit" loading={busy}>
            {amends ? "Add correction" : "Add record"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

export function ResultsToReview({ patientAccountId, patientName }: { patientAccountId: string; patientName?: string }) {
  const { resource, reload, replace } = useResource((signal) => providerApi.pendingLabResults(patientAccountId, signal), patientAccountId);
  const [pending, setPending] = useState<{ result: LabResult; action: "RELEASE" | "RETURN" } | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "critical"; text: string } | null>(null);

  async function review() {
    if (!pending || resource.state !== "ready") return;
    setBusy(true);
    try {
      await providerApi.reviewLabResult(pending.result.id, pending.action);
      replace(resource.data.filter((item) => item.id !== pending.result.id));
      setNotice({
        tone: "success",
        text: pending.action === "RELEASE" ? "Result released. The patient can now see it, and it was added to their record." : "Result returned to the laboratory.",
      });
    } catch (error) {
      setNotice({ tone: "critical", text: actionError(error, "The result could not be reviewed.") });
      reload();
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  return (
    <Card>
      <CardHeader eyebrow="Laboratory" title={patientName ? `Results for ${patientName}` : "Results to review"} action={resource.state === "ready" ? <CountBadge count={resource.data.length} noun="result" /> : undefined} />
      <p className="mt-1.5 text-data text-text-secondary">Patients see a result only after a doctor releases it.</p>
      {notice ? <Alert tone={notice.tone} live={notice.tone === "critical" ? "assertive" : "polite"} title={notice.text} className="mt-4" /> : null}
      {resource.state === "loading" ? (
        <LoadingRows label="Loading results" rows={2} />
      ) : resource.state === "error" ? (
        resource.status === 403 ? <NoAccess message={resource.message} /> : <ErrorRow title="Results could not be loaded." onRetry={reload} status={resource.status} />
      ) : resource.data.length ? (
        <ul aria-label="Results to review" className="mt-4 divide-y divide-line-soft border-t border-border">
          {resource.data.map((result) => (
            <li key={result.id} className="flex flex-col gap-3 py-4">
              <div className="flex flex-wrap items-center gap-3">
                <span aria-hidden className="inline-flex size-10 shrink-0 items-center justify-center rounded-sm bg-warning-soft text-warning">
                  <FlaskConical size={20} strokeWidth={1.8} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-bold text-ink">Laboratory result</span>
                  <span className="block text-caption text-text-secondary">Submitted {formatShortDate(result.createdAt)}</span>
                </span>
                <LabResultStatusBadge status={result.status} />
              </div>
              <p className="ml-13 text-data whitespace-pre-line text-ink [overflow-wrap:anywhere]">{result.summary}</p>
              <div className="ml-13 flex flex-wrap gap-2">
                <Button size="sm" onClick={() => setPending({ result, action: "RELEASE" })}>
                  Release to patient
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setPending({ result, action: "RETURN" })}>
                  Return to laboratory
                </Button>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <StateMessage kind="empty" layout="inline" className="mt-4" title="No results waiting for review." />
      )}
      <ConfirmDialog
        open={pending !== null}
        onClose={() => !busy && setPending(null)}
        onConfirm={() => void review()}
        loading={busy}
        tone={pending?.action === "RETURN" ? "destructive" : "default"}
        title={pending?.action === "RELEASE" ? "Release this result to the patient?" : "Return this result to the laboratory?"}
        description={
          pending?.action === "RELEASE"
            ? "The patient will be able to read it and it is added to their record. This cannot be undone."
            : "The result will not be shown to the patient."
        }
        confirmLabel={pending?.action === "RELEASE" ? "Release result" : "Return result"}
      />
    </Card>
  );
}

function PatientVitals({ patientAccountId }: { patientAccountId: string }) {
  const { resource, reload } = useResource((signal) => providerApi.vitals(patientAccountId, 0, 50, signal), patientAccountId);
  return (
    <Card>
      <CardHeader eyebrow="Patient reported" title="Vital readings" action={resource.state === "ready" ? <CountBadge count={resource.data.totalElements} noun="reading" /> : undefined} />
      <p className="mt-1.5 text-data text-text-secondary">Entered by the patient and shown exactly as recorded.</p>
      {resource.state === "loading" ? (
        <LoadingRows label="Loading vital readings" rows={2} />
      ) : resource.state === "error" ? (
        resource.status === 403 ? <NoAccess message={resource.message} /> : <ErrorRow title="Vital readings could not be loaded." onRetry={reload} status={resource.status} />
      ) : resource.data.content.length ? (
        <ul aria-label="Vital readings" className="mt-4 divide-y divide-line-soft border-t border-border">
          {resource.data.content.map((vital) => (
            <li key={vital.id} className="flex flex-wrap items-center gap-3 py-3">
              <Activity aria-hidden size={18} strokeWidth={1.8} className="text-green-800" />
              <span className="min-w-0 flex-1">
                <span className="block font-bold text-ink">{vital.metric}</span>
                <span className="block text-caption text-text-secondary">{formatWhen(vital.recordedAt)}</span>
              </span>
              <span className="text-data font-semibold text-ink tabular-nums">
                {vital.value} {vital.unit}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <StateMessage kind="empty" layout="inline" className="mt-4" title="No readings recorded." />
      )}
    </Card>
  );
}
