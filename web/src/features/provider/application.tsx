"use client";

import { useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge, type BadgeTone } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { TextInput } from "@/components/ui/inputs";
import { DescriptionList } from "@/components/ui/lists";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { formatDate } from "@/features/patient/format";
import { actionError } from "@/features/workspace/use-resource";
import { NotificationPreferencesPanel } from "@/features/workspace/notification-preferences";
import { PROVIDER_LIMITS, providerApi, type ProviderApplication } from "./provider-api";
import { useProvider } from "./provider-workspace";

/**
 * Provider application status (`provider_applications.status`, V1 migration). "Approved" means an
 * administrator approved the application; MedNet does not independently verify credentials (OQ-09),
 * so the word "verified" is never used.
 */
const STATUS: Record<string, { label: string; tone: BadgeTone; explanation: string }> = {
  APPROVED: { label: "Approved", tone: "success", explanation: "A MedNet administrator approved your application. You are listed in the provider directory." },
  PENDING: { label: "Under review", tone: "warning", explanation: "A MedNet administrator is reviewing your application. Scheduling and patient work open once it is approved." },
  REJECTED: { label: "Not approved", tone: "critical", explanation: "Your application was not approved. You can submit a new application with updated details." },
  SUSPENDED: { label: "Suspended", tone: "critical", explanation: "Your provider listing is suspended. Contact a MedNet administrator; a new application cannot be submitted while it is suspended." },
};

export function applicationStatus(status: string) {
  return STATUS[status] ?? { label: status.toLowerCase(), tone: "neutral" as BadgeTone, explanation: "" };
}

/** Shown in place of every approved-only view until the application is approved. */
export function ApplicationGate() {
  const { application, reloadApplication } = useProvider();
  if (application.state === "loading") {
    return (
      <div aria-busy className="flex flex-col gap-4">
        <span role="status" className="sr-only">
          Checking your provider application
        </span>
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }
  if (application.state === "error") {
    // Every approved-only view sits behind this gate, so a failed load must be retryable in place (Phase 5E).
    return (
      <StateMessage
        kind="error"
        title="Your provider application could not be loaded"
        description="Check your connection, then try again."
        action={
          <Button variant="secondary" onClick={reloadApplication}>
            Try again
          </Button>
        }
      />
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <ApplicationSummary />
      {application.state === "none" || application.application.status === "REJECTED" ? <ApplicationForm /> : null}
    </div>
  );
}

export function ApplicationSummary() {
  const { application } = useProvider();
  if (application.state === "none") {
    return (
      <Card>
        <CardHeader eyebrow="Provider application" title="Apply to provide care on MedNet" />
        <p className="mt-1.5 text-data text-text-secondary">
          Your account is a provider account, but no application has been submitted. After an administrator approves it,
          you can publish open times and manage appointments.
        </p>
      </Card>
    );
  }
  if (application.state !== "ready") return null;
  return <ApplicationDetails application={application.application} />;
}

export function ApplicationDetails({ application }: { application: ProviderApplication }) {
  const status = applicationStatus(application.status);
  return (
    <Card>
      <CardHeader eyebrow="Provider application" title={application.displayName} action={<Badge tone={status.tone}>{status.label}</Badge>} />
      <p className="mt-1.5 text-data text-text-secondary">{status.explanation}</p>
      <DescriptionList
        columns={2}
        className="mt-5"
        items={[
          { term: "Display name", detail: application.displayName },
          { term: "Specialty", detail: application.specialty },
          { term: "Credential reference you submitted", detail: application.credentialReference },
          { term: "Status", detail: status.label },
          { term: "Submitted", detail: formatDate(application.createdAt) },
          { term: "Reviewed", detail: application.reviewedAt ? formatDate(application.reviewedAt) : "Not yet" },
        ]}
      />
      <p className="mt-5 text-caption text-text-secondary">
        These details are changed through a MedNet administrator. The credential reference is shown as submitted; MedNet
        does not independently verify credentials.
      </p>
    </Card>
  );
}

function ApplicationForm() {
  const { reloadApplication } = useProvider();
  const [values, setValues] = useState({ displayName: "", specialty: "", credentialReference: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof typeof values, string>>>({});
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState("");
  const set = (key: keyof typeof values) => (event: { target: { value: string } }) => setValues({ ...values, [key]: event.target.value });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = {
      displayName: values.displayName.trim() ? undefined : "Enter the name patients will see.",
      specialty: values.specialty.trim() ? undefined : "Enter your specialty.",
      credentialReference: values.credentialReference.trim() ? undefined : "Enter your licence or registration reference.",
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
      await providerApi.apply({
        displayName: values.displayName.trim(),
        specialty: values.specialty.trim(),
        credentialReference: values.credentialReference.trim(),
      });
      reloadApplication();
    } catch (error) {
      setFailure(actionError(error, "Your application could not be submitted."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title="Submit an application" titleAs="h3" />
      <form noValidate onSubmit={submit} className="mt-5 grid gap-4 md:grid-cols-2">
        <TextInput label="Display name" required maxLength={PROVIDER_LIMITS.displayName} value={values.displayName} error={errors.displayName} description="For example, Dr. Mariama Cole" onChange={set("displayName")} />
        <TextInput label="Specialty" required maxLength={PROVIDER_LIMITS.specialty} value={values.specialty} error={errors.specialty} onChange={set("specialty")} />
        <TextInput
          label="Licence or registration reference"
          required
          maxLength={PROVIDER_LIMITS.credentialReference}
          value={values.credentialReference}
          error={errors.credentialReference}
          fieldClassName="md:col-span-2"
          onChange={set("credentialReference")}
        />
        {failure ? <Alert tone="critical" live="assertive" title={failure} className="md:col-span-2" /> : null}
        <div className="md:col-span-2">
          <Button type="submit" loading={busy}>
            Submit application
          </Button>
        </div>
      </form>
    </Card>
  );
}

/** /provider/profile — available in every application state. */
export function ProviderProfile() {
  const { session, application } = useProvider();
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
      <ApplicationGate />
      <Card>
        <CardHeader eyebrow="Account" title="Sign-in" titleAs="h3" />
        <DescriptionList className="mt-4" items={[{ term: "Email", detail: session.email }, { term: "Account type", detail: "Provider" }]} />
        <p className="mt-4 text-caption text-text-secondary">
          {application.state === "ready" && application.application.status === "APPROVED"
            ? "Your listing shows your display name, specialty and open times. Patients see “Approved provider”."
            : "Patients cannot find you until your application is approved."}
        </p>
      </Card>
      <NotificationPreferencesPanel />
    </div>
  );
}

/**
 * A PATIENT-role session that has applied to be a provider. The backend grants the PROVIDER role at
 * sign-in only for an approved application, so an application approved after sign-in needs a new
 * sign-in; until then this account uses the patient workspace.
 */
export function ApplicantStatus({
  application,
  onSignOut,
  signOutFailed,
}: {
  application: ProviderApplication;
  onSignOut: () => void;
  signOutFailed: boolean;
}) {
  const approved = application.status === "APPROVED";
  return (
    <div className="flex flex-col gap-6">
      <ApplicationDetails application={application} />
      <Card>
        <CardHeader title={approved ? "Sign in again to open your doctor workspace" : "Until your application is approved"} titleAs="h3" />
        <p className="mt-1.5 text-data text-text-secondary">
          {approved
            ? "Your application was approved after you signed in. Sign out and sign in again to use the doctor workspace."
            : "You can keep using MedNet as a patient. The doctor workspace opens after an administrator approves your application and you sign in again."}
        </p>
        {signOutFailed ? <Alert tone="critical" live="assertive" title="Sign-out failed" className="mt-4" /> : null}
        <div className="mt-5 flex flex-wrap gap-3">
          {approved ? <Button onClick={onSignOut}>Sign out</Button> : <ButtonLink href="/patient">Go to the patient workspace</ButtonLink>}
        </div>
      </Card>
    </div>
  );
}
