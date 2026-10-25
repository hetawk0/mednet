"use client";

import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { FictionalDataBadge } from "@/components/ui/fictional-data-badge";
import { ActivityList, ActivityRow, DescriptionList } from "@/components/ui/lists";
import { Tabs } from "@/components/ui/tabs";
import { fictional } from "@/lib/fictional";
import { ACCOUNT_TYPES } from "./admin-api";
import { AccountsPanel } from "./accounts";
import { AdminPageHeading } from "./frame";
import { AdminAppointments, AuditTrail, ConfigurationView, ReportsView } from "./operations";
import { ProviderApplicationsPanel } from "./providers";
import { TaskQueue } from "./tasks";

const isAccountType = (value: string | undefined) => !!value && (ACCOUNT_TYPES as readonly string[]).includes(value);

/** /admin/users (super administrator). `type` and `status` come from overview links. */
export function UsersAndRolesView({ type, status }: { type?: string; status?: string }) {
  return (
    <>
      <AdminPageHeading
        title="Users and roles"
        description="Find any account, change its details or account type, manage email verification, and suspend or delete it."
      />
      <AccountsPanel
        initialFilters={{
          accountType: isAccountType(type) ? type : "",
          status: status === "ACTIVE" || status === "SUSPENDED" ? status : "",
        }}
      />
    </>
  );
}

export function ProvidersView() {
  return (
    <>
      <AdminPageHeading
        title="Providers"
        description="Review provider applications. Approval is an administrative decision; MedNet does not independently verify credentials."
      />
      <ProviderApplicationsPanel />
    </>
  );
}

/** Email verification queue: the only verification the backend performs. */
export function VerificationView() {
  return (
    <>
      <AdminPageHeading
        title="Verification"
        description="Accounts whose email address is not verified. They cannot sign in until it is."
      />
      <Alert tone="info" title="Email verification only">
        People verify their address with the code MedNet emails them. Mark an address verified here only when it has been
        confirmed another way. MedNet does not verify professional credentials; provider decisions are under Providers.
      </Alert>
      <AccountsPanel
        lockedFilters={{ emailVerified: "false" }}
        allowCreate={false}
        listTitle="Unverified email addresses"
        emptyTitle="Every account's email address is verified"
      />
    </>
  );
}

export function SecurityView() {
  return (
    <>
      <AdminPageHeading
        title="Security"
        description="The administrative audit trail and how access is controlled."
      />
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <AuditTrail />
        <Card>
          <CardHeader eyebrow="Access model" title="How MedNet checks access" titleAs="h3" />
          <DescriptionList
            className="mt-4"
            items={[
              { term: "Sessions", detail: "Server-side session cookie; state-changing requests need a CSRF token." },
              { term: "Administration", detail: "Only administrator and super administrator accounts can call administration endpoints." },
              { term: "Super administrator only", detail: "Editing accounts, account types, email verification and deletion." },
              { term: "Clinical information", detail: "Never available to administrators; doctors need a care relationship and the patient's consent." },
              { term: "Configured administrator", detail: "Changed through deployment settings only." },
            ]}
          />
        </Card>
      </div>
    </>
  );
}

export function ConfigurationPageView() {
  return (
    <>
      <AdminPageHeading
        title="Configuration"
        description="Read-only platform status. MedNet has no configurable settings in this workspace."
      />
      <ConfigurationView />
    </>
  );
}

export function TasksView() {
  return (
    <>
      <AdminPageHeading
        title="Task queue"
        description="Coordinate patient home-care and laboratory requests and track operational references."
      />
      <TaskQueue />
    </>
  );
}

/** /admin/onboarding (administrator): provider decisions and account provisioning. */
export function OnboardingView({ tab }: { tab?: string }) {
  return (
    <>
      <AdminPageHeading
        title="Onboarding"
        description="Review provider applications, create accounts and suspend or reactivate them."
      />
      <Tabs
        label="Onboarding"
        defaultTab={tab === "accounts" ? "accounts" : "providers"}
        items={[
          { id: "providers", label: "Provider applications", content: <ProviderApplicationsPanel /> },
          { id: "accounts", label: "Accounts", content: <AccountsPanel /> },
        ]}
      />
    </>
  );
}

export function AppointmentsView() {
  return (
    <>
      <AdminPageHeading
        title="Appointments"
        description="Every appointment's status and timing, for coordination. Clinical details are not shown."
      />
      <AdminAppointments />
    </>
  );
}

export function ReportsPageView() {
  return (
    <>
      <AdminPageHeading
        title="Reports"
        description="Live operational counts and recent administrative activity. Operational CSV exports are available from their list views; aggregate report exports are not available."
      />
      <ReportsView />
    </>
  );
}

/** Escalations has no backend: presentation-only, labelled fictional (DESIGN_SYSTEM.md §11). */
const FICTIONAL_ESCALATIONS = fictional([
  { ref: "ESC-104", title: "Appointment service issue", meta: "Provider unavailable · example", status: "Escalated" },
  { ref: "ESC-097", title: "Partner capacity", meta: "Coverage reduced in one area · example", status: "Review" },
  { ref: "ESC-091", title: "Rebooking coordination", meta: "Provider cancellation · example", status: "Pending" },
]);

export function EscalationsView() {
  return (
    <>
      <AdminPageHeading
        title="Escalations"
        description="A preview of the approved escalation design. MedNet does not have an escalation workflow yet."
        action={<FictionalDataBadge />}
      />
      <Alert tone="info" title="Escalations are not available yet">
        These rows are example data from the design. Use the task queue for real home-care and laboratory requests.
      </Alert>
      <Card>
        <CardHeader eyebrow="Administrator submenu" title="Escalations" action={<FictionalDataBadge />} />
        <div className="mt-4">
          <ActivityList label="Example escalations">
            {FICTIONAL_ESCALATIONS.data.map((item) => (
              <ActivityRow key={item.ref} marker="ESC" title={item.title} meta={`${item.ref} · ${item.meta}`} trailing={<Badge tone="neutral">{item.status}</Badge>} />
            ))}
          </ActivityList>
        </div>
      </Card>
    </>
  );
}
