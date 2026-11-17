"use client";

import { Bell } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge, type BadgeTone } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { TextInput } from "@/components/ui/inputs";
import { ActivityList, ActivityRow, DescriptionList } from "@/components/ui/lists";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { formatDate, formatShortDate } from "@/features/patient/format";
import type { Notification } from "@/features/patient/patient-api";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { ApiRequestError } from "@/lib/api";
import { actionError, useResource } from "@/features/workspace/use-resource";
import { NotificationPreferencesPanel } from "@/features/workspace/notification-preferences";
import { OnboardingSteps, onboardingStates, type OnboardingStepId } from "./onboarding-steps";
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
  REJECTED: {
    label: "Not approved",
    tone: "critical",
    explanation:
      "A MedNet administrator reviewed your application and did not approve it. No reason is recorded with the decision. You can check your details and submit a corrected application for a new review.",
  },
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
      {application.state === "none" || application.application.status === "REJECTED" ? (
        <ApplicationForm onSubmitted={reloadApplication} />
      ) : null}
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

export function ApplicationDetails({
  application,
  showExplanation = true,
}: {
  application: ProviderApplication;
  /** Off where the status is already explained above (the onboarding dashboard). */
  showExplanation?: boolean;
}) {
  const status = applicationStatus(application.status);
  return (
    <Card>
      <CardHeader eyebrow="Provider application" title={application.displayName} action={<Badge tone={status.tone}>{status.label}</Badge>} />
      {showExplanation ? <p className="mt-1.5 text-data text-text-secondary">{status.explanation}</p> : null}
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

/**
 * `POST /providers/applications`. A 409 means an application already exists (for example submitted from
 * another tab), so the caller reloads and shows it instead of offering a duplicate submission.
 */
export function ApplicationForm({
  onSubmitted,
  title = "Submit an application",
  intro,
  submitLabel = "Submit application",
}: {
  onSubmitted: () => void;
  title?: string;
  intro?: string;
  submitLabel?: string;
}) {
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
      onSubmitted();
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 409) return onSubmitted();
      setFailure(actionError(error, "Your application could not be submitted."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={title} titleAs="h3" />
      {intro ? <p className="mt-1.5 text-data text-text-secondary">{intro}</p> : null}
      <form noValidate onSubmit={submit} className="mt-5 grid gap-4 md:grid-cols-2" aria-label={title}>
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
            {submitLabel}
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

const DASHBOARD: Record<string, { title: string; current: OnboardingStepId | "done"; attention?: boolean }> = {
  PENDING: { title: "Your application is under review", current: "review" },
  REJECTED: { title: "Your application was not approved", current: "details", attention: true },
  SUSPENDED: { title: "Your provider listing is suspended", current: "review", attention: true },
  APPROVED: { title: "Your application was approved", current: "done" },
};

/**
 * Pending-provider dashboard for a PATIENT-role session: a provider sign-up that has not been approved
 * (`application` null until professional details are submitted) or a patient who applied. The API grants
 * the PROVIDER role at sign-in only for an approved application and signs the applicant out when a review
 * decision is recorded, so nothing here unlocks clinical features; the API refuses them for this session.
 */
export function ApplicantDashboard({
  application,
  providerAccount,
  onApplicationChanged,
  onSignOut,
  signOutFailed,
}: {
  application: ProviderApplication | null;
  /** Signed up as a provider (account type PROVIDER) rather than a patient who later applied. */
  providerAccount: boolean;
  /** Reloads the session and application, e.g. after submitting or to check the review status. */
  onApplicationChanged: () => void;
  onSignOut: () => void;
  signOutFailed: boolean;
}) {
  const view = application ? DASHBOARD[application.status] : null;
  const status = application ? applicationStatus(application.status) : null;
  const states = onboardingStates(view?.current ?? "details", view?.attention);
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          eyebrow="Provider onboarding"
          title={view?.title ?? (application ? status!.label : "Submit your professional details")}
          action={status ? <Badge tone={status.tone}>{status.label}</Badge> : undefined}
        />
        <p className="mt-1.5 text-data text-text-secondary">
          {status
            ? status.explanation
            : "Your account and email are confirmed. Tell MedNet who you are and how your practice is registered so an administrator can review your application."}
        </p>
        <OnboardingSteps states={states} detail={status ? { review: status.label } : undefined} className="mt-5" />
      </Card>

      {!application ? (
        <ApplicationForm
          onSubmitted={onApplicationChanged}
          title="Professional details"
          intro="Patients see your display name and specialty once you are approved. Administrators use your licence or registration reference to review your application; MedNet does not independently verify credentials."
        />
      ) : (
        <>
          <ApplicationDetails application={application} showExplanation={false} />
          <ApplicationUpdates />
        </>
      )}

      {application?.status === "REJECTED" ? (
        <ApplicationForm
          onSubmitted={onApplicationChanged}
          title="Submit corrected details"
          intro="Check that your display name, specialty and licence or registration reference are complete and match your registration, then submit them for a new review. Your previous application stays on record as not approved."
          submitLabel="Submit corrected application"
        />
      ) : null}

      <Card>
        <CardHeader title={application?.status === "APPROVED" ? "Sign in again to open your doctor workspace" : "What happens next"} titleAs="h3" />
        <p className="mt-1.5 text-data text-text-secondary">
          {!application
            ? "After you submit your details, an administrator reviews them. Scheduling, appointments and patient records stay closed until your application is approved."
            : application.status === "PENDING"
              ? "When an administrator records a decision, MedNet signs you out so your new access applies. Sign in again to see the outcome; an approved application opens the doctor workspace."
              : application.status === "APPROVED"
                ? "Your application was approved after you signed in. Sign out and sign in again to use the doctor workspace."
                : application.status === "SUSPENDED"
                  ? "Scheduling and patient work stay closed while your listing is suspended."
                  : "A corrected application is reviewed like a new one, and scheduling and patient work stay closed until it is approved. If you believe the decision is wrong, contact a MedNet administrator."}
        </p>
        {signOutFailed ? <Alert tone="critical" live="assertive" title="Sign-out failed" className="mt-4" /> : null}
        <div className="mt-5 flex flex-wrap gap-3">
          {application?.status === "APPROVED" ? (
            <Button onClick={onSignOut}>Sign out</Button>
          ) : (
            <>
              {application?.status === "PENDING" ? (
                <Button variant="secondary" onClick={onApplicationChanged}>
                  Check status
                </Button>
              ) : null}
              {!providerAccount ? <ButtonLink href="/patient">Go to the patient workspace</ButtonLink> : null}
              <Button variant="secondary" onClick={onSignOut}>
                Sign out
              </Button>
            </>
          )}
        </div>
      </Card>
    </div>
  );
}

/**
 * Review decisions on the dashboard, from the existing notification feed filtered to PROVIDER_APPLICATION.
 * Applicants never reach the patient workspace, so this is where they read and dismiss these notices.
 */
export function ApplicationUpdates() {
  const { resource, reload, replace } = useResource((signal) => providerApi.applicationUpdates(signal), "application-updates");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState("");

  async function markRead(item: Notification) {
    setBusyId(item.id);
    setError("");
    try {
      const updated = await providerApi.markNotificationRead(item.id);
      if (resource.state === "ready") {
        replace({ ...resource.data, content: resource.data.content.map((entry) => (entry.id === updated.id ? updated : entry)) });
      }
    } catch (failure) {
      setError(actionError(failure, "The update could not be marked as read."));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card>
      <CardHeader eyebrow="Updates" title="Application updates" titleAs="h3" />
      <div className="mt-4 border-t border-border">
        {error ? <StateMessage kind="error" layout="inline" className="mt-4" title={error} /> : null}
        {resource.state === "loading" ? (
          <LoadingRows label="Loading application updates" rows={1} />
        ) : resource.state === "error" ? (
          <ErrorRow title="Application updates could not be loaded." onRetry={reload} status={resource.status} />
        ) : resource.data.content.length ? (
          <ActivityList label="Application updates">
            {resource.data.content.map((item) => (
              <ActivityRow
                key={item.id}
                icon={Bell}
                tileTone={item.readAt ? "neutral" : "info"}
                title={item.title}
                meta={`${formatShortDate(item.createdAt)}${item.readAt ? " · Read" : " · Unread"}`}
                trailing={
                  item.readAt ? undefined : (
                    <Button variant="tertiary" size="sm" loading={busyId === item.id} onClick={() => void markRead(item)}>
                      Mark as read
                    </Button>
                  )
                }
              />
            ))}
          </ActivityList>
        ) : (
          <StateMessage
            kind="empty"
            layout="inline"
            className="mt-4"
            title="No decisions yet."
            description="When an administrator approves or does not approve your application, the decision appears here."
          />
        )}
      </div>
    </Card>
  );
}
