"use client";

import { useState, type ReactNode } from "react";
import { StatusTimeline, type TimelineStep } from "@/components/healthcare/status-timeline";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { FictionalDataBadge } from "@/components/ui/fictional-data-badge";
import { Checkbox } from "@/components/ui/inputs";
import { ActivityList, ActivityRow, DescriptionList } from "@/components/ui/lists";
import { MetricCard, MetricGrid } from "@/components/ui/metric-card";
import { Pagination } from "@/components/ui/pagination";
import { StateMessage } from "@/components/ui/state-message";
import { fictional } from "@/lib/fictional";
import { ErrorRow, LoadingRows } from "@/features/workspace/states";
import { OperationsPageHeading, formatOperationsDateTime } from "@/features/workspace/record-layout";
import { NotificationPreferencesPanel } from "@/features/workspace/notification-preferences";
import { useResource } from "@/features/workspace/use-resource";
import { labApi } from "./lab-api";
import { useLaboratory } from "./laboratory-workspace";
import { OrderQueue } from "./orders";
import { countRequests, labReference, labResultReview } from "./status";

const EYEBROW = "Laboratory partner";

/** The stages MedNet supports today, in order (Figma "Processing stages" reduced to real states). */
const SUPPORTED_STAGES: TimelineStep[] = [
  { id: "assigned", title: "Assigned by MedNet", state: "complete", stateLabel: "Administrator", detail: "Request assigned to your account" },
  { id: "processing", title: "Processing started", state: "current", stateLabel: "Laboratory", detail: "You start the order" },
  { id: "result", title: "Result submitted", state: "pending", stateLabel: "Laboratory", detail: "Sent for doctor review, not to the patient" },
  { id: "review", title: "Doctor review", state: "required", stateLabel: "Doctor", detail: "Release or return" },
  { id: "released", title: "Released to patient", state: "locked", stateLabel: "Doctor", detail: "Order closes" },
];

export function LaboratoryOverview() {
  const { requests, reloadRequests } = useLaboratory();
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Order operations"
        description="Review assigned orders, process them and submit results for doctor review."
        action={
          <ButtonLink href="/laboratory/orders" variant="secondary">
            All orders
          </ButtonLink>
        }
      />
      {requests.state === "loading" ? (
        <LoadingRows label="Loading your order counts" rows={2} />
      ) : requests.state === "error" ? (
        <ErrorRow title="Your order counts could not be loaded" status={requests.status} onRetry={reloadRequests} />
      ) : (
        <Counts />
      )}
      <OrderQueue limit={5} defaultStatus="" />
      <Card>
        <CardHeader
          eyebrow="Specimen and result workflow"
          title="Processing stages"
          action={
            <ButtonLink href="/laboratory/specimens" variant="secondary" size="sm">
              Specimen steps
            </ButtonLink>
          }
        />
        <div className="mt-5">
          <StatusTimeline orientation="horizontal" label="Supported processing stages" steps={SUPPORTED_STAGES} />
        </div>
        <Alert tone="warning" title="Submitting does not release a result." className="mt-5">
          A submitted result stays private until a doctor caring for the patient reviews and releases it. Specimen receipt,
          custody and laboratory-side validation steps are not available in MedNet yet.
        </Alert>
      </Card>
    </>
  );
}

function Counts() {
  const { requests } = useLaboratory();
  const counts = countRequests(requests.state === "ready" ? requests.data : []);
  return (
    <MetricGrid>
      <MetricCard tone="warning" value={counts.notStarted} label="Not started" hint="Assigned, waiting for you" />
      <MetricCard tone="info" value={counts.processing} label="Processing" hint="Result to submit or under doctor review" />
      <MetricCard tone="success" value={counts.released} label="Released" hint="Result released by a doctor" />
      <MetricCard tone="neutral" value={counts.cancelled} label="Cancelled" hint={`${counts.total} assigned in total`} />
    </MetricGrid>
  );
}

export function LaboratoryOrders() {
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Orders"
        description="Every laboratory request assigned to your account. Start processing, then submit the result for doctor review."
      />
      <OrderQueue />
    </>
  );
}

const RESULT_GROUPS: Array<{ status: string; title: string; empty: string }> = [
  { status: "RETURNED", title: "Returned by a doctor", empty: "No returned results." },
  { status: "PENDING_REVIEW", title: "Awaiting doctor review", empty: "No results are awaiting review." },
  { status: "RELEASED", title: "Released to the patient", empty: "No results have been released yet." },
];

/** Results per page in each review-status group. */
export const RESULTS_PAGE_SIZE = 10;

/**
 * Results: the laboratory's own submissions with the doctor's review outcome, one paged list per review
 * status (`GET /partner/lab-results?status=&page=`, Phase 5D; before, the newest 100 overall). Read-only;
 * the laboratory never releases a result.
 */
export function LaboratoryResults() {
  return (
    <>
      <OperationsPageHeading
        eyebrow={EYEBROW}
        title="Results"
        description="Results you submitted and what the reviewing doctor decided. Patients see a result only after a doctor releases it."
      />
      <div className="grid items-start gap-6 xl:grid-cols-3">
        {RESULT_GROUPS.map((group) => (
          <ResultGroup key={group.status} {...group} />
        ))}
      </div>
      <p className="text-caption text-text-secondary">
        A returned result stays with you: open the order to submit a correction. Only the reviewing doctor can release or return a result.
      </p>
    </>
  );
}

function ResultGroup({ status, title, empty }: { status: string; title: string; empty: string }) {
  const { requests } = useLaboratory();
  const [page, setPage] = useState(0);
  const { resource, reload } = useResource((signal) => labApi.resultsPage(status, page, RESULTS_PAGE_SIZE, signal), `${status}-${page}`);
  const tests = new Map((requests.state === "ready" ? requests.data : []).map((request) => [request.id, request.requestedService]));
  const review = labResultReview(status);
  return (
    <Card>
      <CardHeader
        eyebrow="Result workflow"
        title={title}
        action={resource.state === "ready" ? <Badge tone={review.tone}>{`${resource.data.totalElements}`}</Badge> : undefined}
      />
      <div className="mt-4 flex flex-col gap-4">
        {resource.state === "loading" ? (
          <LoadingRows label={`Loading: ${title}`} rows={2} />
        ) : resource.state === "error" ? (
          <ErrorRow title={`${title} could not be loaded`} status={resource.status} onRetry={reload} />
        ) : resource.data.content.length ? (
          <ActivityList label={title}>
            {resource.data.content.map((result) => (
              <ActivityRow
                key={result.id}
                marker="LAB"
                tileTone={status === "RELEASED" ? "success" : status === "RETURNED" ? "warning" : "info"}
                title={`${labReference(result.requestId)} · ${tests.get(result.requestId) ?? "Laboratory request"}`}
                meta={
                  status === "PENDING_REVIEW"
                    ? `Submitted ${formatOperationsDateTime(result.createdAt)}`
                    : `Submitted ${formatOperationsDateTime(result.createdAt)} · ${status === "RETURNED" ? "returned" : "released"} ${formatOperationsDateTime(result.reviewedAt ?? result.createdAt)}`
                }
                href={status === "RETURNED" ? "/laboratory/orders" : undefined}
              />
            ))}
          </ActivityList>
        ) : (
          <StateMessage kind="empty" layout="inline" title={empty} />
        )}
        {resource.state === "ready" ? (
          <Pagination
            page={resource.data.number + 1}
            totalPages={resource.data.totalPages}
            onPageChange={(next) => setPage(next - 1)}
            label={`${title}: pages`}
          />
        ) : null}
      </div>
    </Card>
  );
}

export function LaboratoryProfile() {
  const { session } = useLaboratory();
  return (
    <>
      <OperationsPageHeading eyebrow={EYEBROW} title="Your account" description="The account MedNet assigns laboratory requests to." />
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <CardHeader eyebrow="Account" title="Sign-in" titleAs="h3" />
          <DescriptionList
            className="mt-4"
            items={[
              { term: "Email", detail: session.email },
              { term: "Account type", detail: "Laboratory partner" },
              { term: "Organization", detail: "Not recorded in MedNet (accounts are individual)" },
            ]}
          />
          <p className="mt-4 text-caption text-text-secondary">Name, email and password changes go through a MedNet administrator.</p>
        </Card>
        <NotificationPreferencesPanel />
        <Card>
          <CardHeader eyebrow="Permissions" title="What this account can do" titleAs="h3" />
          <ul className="mt-4 flex list-disc flex-col gap-2 pl-5 text-data text-ink-soft">
            <li>See laboratory requests assigned to this account.</li>
            <li>Start processing an assigned request.</li>
            <li>Submit a result for doctor review, and a corrected result if one is returned.</li>
            <li>It cannot release results, see patient identity or records, or change other requests.</li>
          </ul>
        </Card>
      </div>
    </>
  );
}

/* ---------- Presentation-only views (no backend; DESIGN_SYSTEM.md §11) ---------- */

function PresentationFrame({
  title,
  description,
  unavailable,
  notice,
  children,
}: {
  title: string;
  description: string;
  unavailable: string;
  notice: string;
  children: ReactNode;
}) {
  return (
    <>
      <OperationsPageHeading eyebrow={EYEBROW} title={title} description={description} action={<FictionalDataBadge />} />
      <Alert tone="info" title={unavailable}>
        {notice}
      </Alert>
      {children}
    </>
  );
}

const SPECIMEN_STEPS: TimelineStep[] = [
  { id: "collected", title: "Specimen collected", state: "locked", stateLabel: "Future", detail: "Identity, label and packaging checks" },
  { id: "custody", title: "Custody transport", state: "locked", stateLabel: "Future", detail: "Every transfer timestamped" },
  { id: "receipt", title: "Laboratory receipt", state: "locked", stateLabel: "Future", detail: "Condition accepted or rejected" },
  { id: "validation", title: "Professional validation", state: "locked", stateLabel: "Future", detail: "Authorized laboratory sign-off" },
];

export function LaboratorySpecimens() {
  return (
    <PresentationFrame
      title="Specimens"
      unavailable="Specimen tracking is not available in MedNet yet"
      description="Preview of the approved specimen design. Nothing on this page records a clinical event."
      notice="Specimen collection, chain of custody, receipt checks and specimen rejection have no backend yet. They are kept here as future requirements; use Orders for real work."
    >
      <Card>
        <CardHeader eyebrow="Future requirement" title="Specimen lifecycle" action={<FictionalDataBadge />} />
        <div className="mt-5">
          <StatusTimeline orientation="horizontal" label="Future specimen steps" steps={SPECIMEN_STEPS} />
        </div>
      </Card>
      <Card>
        <CardHeader eyebrow="Future requirement" title="Specimen receipt checklist" action={<FictionalDataBadge />} />
        <fieldset disabled className="mt-4">
          <legend className="sr-only">Specimen receipt checklist (not available)</legend>
          <Checkbox label="Label and order match" />
          <Checkbox label="Container and seal intact" />
          <Checkbox label="Temperature exception recorded" />
        </fieldset>
        <p className="mt-3 text-caption text-text-secondary">Disabled: these checks are not stored by MedNet.</p>
      </Card>
    </PresentationFrame>
  );
}

const CATALOGUE = fictional([
  { code: "CBC", name: "Complete blood count", detail: "Whole blood · example" },
  { code: "GLU", name: "Blood glucose", detail: "Plasma · example" },
  { code: "LIP", name: "Lipid profile", detail: "Serum · example" },
]);

export function LaboratoryCatalogue() {
  return (
    <PresentationFrame
      title="Test catalogue"
      unavailable="A test catalogue is not available in MedNet yet"
      description="Preview of the approved catalogue design."
      notice="MedNet has no test catalogue. Patients describe the test they need in their own words, and that text is what you see on each order."
    >
      <Card>
        <CardHeader eyebrow="Example data" title="Tests" action={<FictionalDataBadge />} />
        <div className="mt-4">
          <ActivityList label="Example tests">
            {CATALOGUE.data.map((test) => (
              <ActivityRow key={test.code} marker={test.code} title={test.name} meta={test.detail} />
            ))}
          </ActivityList>
        </div>
      </Card>
    </PresentationFrame>
  );
}

const PAYOUTS = fictional([
  { label: "Available balance", value: "USD 0.00", note: "Example only" },
  { label: "Pending settlement", value: "None", note: "Example only" },
  { label: "Next payout", value: "Not scheduled", note: "No payouts on MedNet" },
  { label: "Payout method", value: "None", note: "No payment details are collected" },
]);

export function LaboratoryPricing() {
  return (
    <PresentationFrame
      title="Pricing and payouts"
      unavailable="Pricing and payouts are not available on MedNet"
      description="Preview of the approved partner finance design."
      notice="MedNet does not take payments or make payouts. No amount here relates to a real order."
    >
      <Card>
        <CardHeader eyebrow="Example data" title="Laboratory partner pricing and payouts" action={<FictionalDataBadge />} />
        <dl className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {PAYOUTS.data.map((item) => (
            <div key={item.label} className="rounded-sm border border-border p-4">
              <dt className="text-caption text-text-muted">{item.label}</dt>
              <dd className="mt-0.5 text-[1.0625rem] font-bold text-green-950">{item.value}</dd>
              <dd className="text-caption text-text-secondary">{item.note}</dd>
            </div>
          ))}
        </dl>
      </Card>
    </PresentationFrame>
  );
}

const AUDIT = fictional([
  { time: "09:42", title: "Order started", actor: "Example event" },
  { time: "10:15", title: "Result submitted for doctor review", actor: "Example event" },
]);

export function LaboratoryAudit() {
  return (
    <PresentationFrame
      title="Audit history"
      unavailable="Laboratory audit history is not available yet"
      description="Preview of the approved audit design."
      notice="MedNet records every laboratory action in its audit trail, but laboratories cannot read it yet. Administrators can."
    >
      <Card>
        <CardHeader eyebrow="Example data" title="Audit history" action={<FictionalDataBadge />} />
        <ul aria-label="Example audit events" className="mt-4 divide-y divide-line-soft">
          {AUDIT.data.map((event) => (
            <li key={event.time} className="grid grid-cols-[3.5rem_1fr] gap-3 py-3">
              <span className="text-caption font-semibold text-text-secondary tabular-nums">{event.time}</span>
              <span>
                <span className="block font-bold text-ink">{event.title}</span>
                <span className="block text-caption text-text-secondary">{event.actor}</span>
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </PresentationFrame>
  );
}
