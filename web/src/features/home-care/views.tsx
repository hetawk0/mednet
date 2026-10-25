"use client";

import { StatusTimeline, type TimelineStep } from "@/components/healthcare/status-timeline";
import { Alert } from "@/components/ui/alert";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { DescriptionList } from "@/components/ui/lists";
import { MetricCard, MetricGrid } from "@/components/ui/metric-card";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { OperationsPageHeading } from "@/features/workspace/record-layout";
import { NotificationPreferencesPanel } from "@/features/workspace/notification-preferences";
import { useHomeCare } from "./home-care-workspace";
import { VisitQueue } from "./visits";
import { countHomeCare } from "./status";

const EYEBROW = "Home-care partner";

/** The stages MedNet supports today, in order. */
const SUPPORTED_STAGES: TimelineStep[] = [
  { id: "requested", title: "Patient requests home care", state: "complete", stateLabel: "Patient", detail: "Care needed and visit location" },
  { id: "assigned", title: "Assigned by MedNet", state: "complete", stateLabel: "Administrator", detail: "To one home-care staff member" },
  { id: "scheduled", title: "Visit time set", state: "current", stateLabel: "You", detail: "Once; the patient is notified" },
  { id: "started", title: "Visit started", state: "pending", stateLabel: "You", detail: "On arrival" },
  { id: "completed", title: "Visit completed", state: "pending", stateLabel: "You", detail: "The patient is notified" },
];

export function HomeCareOverview() {
  const { requests, reloadRequests } = useHomeCare();
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Home-care visits"
        description="Set visit times for the requests assigned to you, then record when each visit starts and is completed."
        action={
          <ButtonLink href="/home-care/visits" variant="secondary">
            All visits
          </ButtonLink>
        }
      />
      {requests.state === "loading" ? (
        <LoadingRows label="Loading your visit counts" rows={2} />
      ) : requests.state === "error" ? (
        <ErrorRow title="Your visit counts could not be loaded" status={requests.status} onRetry={reloadRequests} />
      ) : (
        <Counts />
      )}
      <VisitQueue limit={5} title="Next visits" />
      <Card>
        <CardHeader eyebrow="Home-care workflow" title="Visit stages" />
        <div className="mt-5">
          <StatusTimeline orientation="horizontal" label="Supported visit stages" steps={SUPPORTED_STAGES} />
        </div>
        <Alert tone="info" title="MedNet records visit times and status only." className="mt-5">
          Visit notes, care plans, travel tracking and patient contact details are not part of MedNet. A patient can cancel a
          request until the visit starts; an administrator can cancel it until it is completed.
        </Alert>
      </Card>
    </>
  );
}

function Counts() {
  const { requests } = useHomeCare();
  const counts = countHomeCare(requests.state === "ready" ? requests.data : []);
  return (
    <MetricGrid>
      <MetricCard tone="warning" value={counts.unscheduled} label="Need a visit time" hint="Assigned, not yet scheduled" />
      <MetricCard tone="info" value={counts.scheduled} label="Scheduled" hint="Visit time set" />
      <MetricCard tone="info" value={counts["in-progress"]} label="In progress" hint="Visit started" />
      <MetricCard tone="success" value={counts.completed} label="Completed" hint={`${counts.cancelled} cancelled · ${counts.total} in total`} />
    </MetricGrid>
  );
}

export function HomeCareVisits() {
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Visits"
        description="Every home-care request assigned to your account. Set the visit time, start the visit and mark it completed."
      />
      <VisitQueue />
    </>
  );
}

export function HomeCareProfile() {
  const { session } = useHomeCare();
  return (
    <>
      <OperationsPageHeading eyebrow={EYEBROW} title="Your account" description="The account MedNet assigns home-care requests to." />
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <CardHeader eyebrow="Account" title="Sign-in" titleAs="h3" />
          <DescriptionList
            className="mt-4"
            items={[
              { term: "Email", detail: session.email },
              { term: "Account type", detail: "Home-care partner" },
              { term: "Organization", detail: "Not recorded in MedNet (accounts are individual)" },
            ]}
          />
          <p className="mt-4 text-caption text-text-secondary">
            Name and email changes go through a MedNet administrator. To change your password, sign out and use
            &ldquo;Forgot password&rdquo; on the sign-in page; you are signed out everywhere when it changes.
          </p>
        </Card>
        <NotificationPreferencesPanel />
        <Card>
          <CardHeader eyebrow="Permissions" title="What this account can do" titleAs="h3" />
          <ul className="mt-4 flex list-disc flex-col gap-2 pl-5 text-data text-ink-soft">
            <li>See home-care requests assigned to this account: the care requested and the visit location.</li>
            <li>Set the visit time once, start the visit and mark it completed.</li>
            <li>It cannot see patient names, contact details or medical records, laboratory work, or other staff members&rsquo; requests.</li>
          </ul>
        </Card>
      </div>
    </>
  );
}

const FAQ: Array<{ question: string; answer: string }> = [
  {
    question: "How do requests reach me?",
    answer:
      "A patient asks for home care in MedNet, describing the care needed and where to visit. A MedNet administrator assigns the request to one home-care staff member. You get a notification and it appears under Visits.",
  },
  {
    question: "What if the visit time has to change?",
    answer:
      "MedNet sets the visit time once. If it must move, contact a MedNet administrator; there is no reschedule action in the workspace.",
  },
  {
    question: "How do I contact the patient?",
    answer:
      "MedNet does not share patient names or contact details with home-care staff. The patient sees the visit time you set. Arrange anything else through a MedNet administrator.",
  },
  {
    question: "What happens if a request is cancelled?",
    answer:
      "The patient can cancel until the visit starts, and an administrator until it is completed. The request then shows as cancelled; do not visit. If you act on a request that was just cancelled, MedNet refuses the change and refreshes the list.",
  },
  {
    question: "Where do I record what care was given?",
    answer: "Nowhere in MedNet yet. Marking the visit completed records only that it happened; clinical notes stay with the patient's doctor.",
  },
];

export function HomeCareHelp() {
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Help and guidance"
        description="How home-care requests work in MedNet, and what the workspace does not do."
      />
      <Card>
        <CardHeader eyebrow="Questions" title="Working with home-care requests" />
        <dl className="mt-4 flex flex-col divide-y divide-line-soft">
          {FAQ.map((item) => (
            <div key={item.question} className="py-4">
              <dt className="font-semibold text-ink">{item.question}</dt>
              <dd className="mt-1 text-data text-ink-soft">{item.answer}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <Alert tone="warning" title="Not for emergencies">
        MedNet does not replace emergency services.
      </Alert>
    </>
  );
}
