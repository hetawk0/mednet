"use client";

import { type FormEvent, useEffect, useState } from "react";
import Link from "next/link";

type AccessState = "checking" | "signed-out" | "signed-in";

type AdminSession = {
  email: string;
  role: string;
};

type AdminOverview = {
  apiStatus: string;
  checkedAt: string;
  uptimeSeconds: number;
  counts: {
    pendingProviders: number;
    activeAccounts: number;
    openRequests: number;
  } | null;
  modules: Array<{ key: string; status: string }>;
};

type AdminTab = "overview" | "providers" | "accounts" | "requests" | "audit";

type ProviderApplication = {
  id: string;
  displayName: string;
  email: string;
  specialty: string;
  credentialReference: string;
  status: string;
  createdAt: string;
};

type PlatformAccount = {
  id: string;
  email: string;
  accountType: string;
  status: string;
  emailVerified: boolean;
  createdAt: string;
};

type AccountPage = {
  content: PlatformAccount[];
  number: number;
  totalPages: number;
  totalElements: number;
};

type ServiceRequest = {
  id: string;
  referenceId: string;
  requestType: string;
  requesterEmail: string;
  status: string;
  createdAt: string;
};

type AdminAuditEvent = {
  id: string;
  actorEmail: string;
  action: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

const moduleLabels: Record<string, string> = {
  providerReview: "Provider applications",
  accountSupport: "Account registry",
  serviceRequests: "Service request queue",
  auditTrail: "Administrative audit trail",
};

const moduleKeys: Record<Exclude<AdminTab, "overview">, string> = {
  providers: "providerReview",
  accounts: "accountSupport",
  requests: "serviceRequests",
  audit: "auditTrail",
};

export function AdminPortal() {
  const [access, setAccess] = useState<AccessState>("checking");
  const [session, setSession] = useState<AdminSession | null>(null);
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [activeTab, setActiveTab] = useState<AdminTab>("overview");
  const [providers, setProviders] = useState<ProviderApplication[]>([]);
  const [accounts, setAccounts] = useState<PlatformAccount[]>([]);
  const [accountPage, setAccountPage] = useState({
    number: 0,
    totalPages: 0,
    totalElements: 0,
  });
  const [requests, setRequests] = useState<ServiceRequest[]>([]);
  const [auditEvents, setAuditEvents] = useState<AdminAuditEvent[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  async function refreshOverview() {
    const response = await fetch("/api/v1/admin/overview", {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!response.ok) throw new Error("The admin API could not be reached.");
    setOverview((await response.json()) as AdminOverview);
  }

  useEffect(() => {
    let active = true;

    async function loadSession() {
      try {
        const response = await fetch("/api/v1/auth/admin/session", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!active) return;
        if (!response.ok) {
          setAccess("signed-out");
          return;
        }

        const currentSession = (await response.json()) as AdminSession;
        const overviewResponse = await fetch("/api/v1/admin/overview", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (!overviewResponse.ok) {
          throw new Error("The admin API could not be reached.");
        }

        const currentOverview =
          (await overviewResponse.json()) as AdminOverview;
        if (!active) return;
        setSession(currentSession);
        setOverview(currentOverview);
        setAccess("signed-in");
      } catch {
        if (!active) return;
        setError("The MedNet API is unavailable. Try again shortly.");
        setAccess("signed-out");
      }
    }

    void loadSession();
    return () => {
      active = false;
    };
  }, []);

  async function handleLogout() {
    setBusy(true);
    setError("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        cache: "no-store",
        credentials: "same-origin",
      });
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
      });
      setSession(null);
      setOverview(null);
      setActiveTab("overview");
      setAccess("signed-out");
    } catch {
      setError("Sign-out failed. Close this browser session and try again.");
    } finally {
      setBusy(false);
    }
  }

  async function reloadTab(tab: Exclude<AdminTab, "overview">, requestedPage = 0) {
    const endpoint = tab === "audit" ? "audit" : tab;
    const query = tab === "accounts" ? `?page=${requestedPage}&size=25` : "";
    const response = await fetch(`/api/v1/admin/${endpoint}${query}`, {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!response.ok)
      throw new Error(
        `Could not load ${moduleLabels[moduleKeys[tab]].toLowerCase()}.`,
      );
    const data = (await response.json()) as unknown;

    if (tab === "providers") setProviders(data as ProviderApplication[]);
    if (tab === "accounts") {
      const accountResult = data as AccountPage;
      setAccounts(accountResult.content ?? []);
      setAccountPage({
        number: accountResult.number,
        totalPages: accountResult.totalPages,
        totalElements: accountResult.totalElements,
      });
    }
    if (tab === "requests") setRequests(data as ServiceRequest[]);
    if (tab === "audit") setAuditEvents(data as AdminAuditEvent[]);
  }

  async function openTab(tab: AdminTab) {
    setActiveTab(tab);
    setError("");
    if (tab === "overview") {
      await refreshOverview().catch(() =>
        setError("The admin API could not be reached."),
      );
      return;
    }

    const moduleStatus = overview?.modules.find(
      (module) => module.key === moduleKeys[tab],
    )?.status;
    if (moduleStatus !== "CONNECTED") return;

    try {
      await reloadTab(tab);
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : "Could not load this workflow.",
      );
    }
  }

  async function mutateWorkflow(
    path: string,
    method: "POST" | "PATCH",
    body: unknown,
  ) {
    const csrfResponse = await fetch("/api/v1/auth/csrf", {
      cache: "no-store",
      credentials: "same-origin",
    });
    if (!csrfResponse.ok)
      throw new Error("Could not initialize the secure request.");
    const csrf = (await csrfResponse.json()) as CsrfResponse;
    const response = await fetch(`/api/v1/admin/${path}`, {
      method,
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok)
      throw new Error(`Admin operation failed (${response.status}).`);
  }

  async function createRecord(
    event: FormEvent<HTMLFormElement>,
    path: string,
    tab: Exclude<AdminTab, "overview">,
  ) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload: Record<string, string> = {};
    new FormData(form).forEach((value, key) => {
      payload[key] = String(value);
    });

    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(path, "POST", payload);
      await reloadTab(tab);
      await refreshOverview();
      form.reset();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not save this record.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function changeRecordStatus(
    path: string,
    id: string,
    status: string,
    tab: Exclude<AdminTab, "overview" | "audit">,
  ) {
    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(`${path}/${id}/status`, "PATCH", { status });
      await reloadTab(tab);
      await refreshOverview();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not update this record.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function changeAccountRole(id: string, accountType: string) {
    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(`accounts/${id}/role`, "PATCH", { accountType });
      await reloadTab("accounts");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not change the account role.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function deleteAccount(id: string) {
    setBusy(true);
    setError("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        credentials: "same-origin",
      });
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch(`/api/v1/admin/accounts/${id}`, {
        method: "DELETE",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
      });
      if (!response.ok)
        throw new Error(`Account deletion failed (${response.status}).`);
      await reloadTab("accounts");
      await refreshOverview();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not delete the account.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function changeAccountPage(page: number) {
    setBusy(true);
    try {
      await reloadTab("accounts", page);
    } catch {
      setError("Could not load that account page.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="admin-shell">
      <header className="admin-header">
        <Link className="admin-brand" href="/" aria-label="MedNet home">
          <span className="admin-brand-mark" aria-hidden="true">
            M
          </span>
          <span>MedNet</span>
        </Link>
        <div className="admin-header-label">
          <span className="admin-header-kicker">Platform operations</span>
          <span>Administration</span>
        </div>
        {access === "signed-in" && session ? (
          <div className="admin-identity">
            <button
              className="admin-user-menu-button"
              type="button"
              aria-expanded={userMenuOpen}
              aria-haspopup="menu"
              onClick={() => setUserMenuOpen((open) => !open)}
            >
              <span className="admin-avatar" aria-hidden="true">
                {session.email.slice(0, 1).toUpperCase()}
              </span>
              <span className="admin-user-summary">
                <strong>{session.email}</strong>
                <small>Administrator</small>
              </span>
              <span className="admin-menu-chevron" aria-hidden="true">
                {userMenuOpen ? "-" : "+"}
              </span>
            </button>
            {userMenuOpen && (
              <div className="admin-user-menu" role="menu">
                <Link href="/account" role="menuitem">
                  Account settings
                </Link>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => void handleLogout()}
                  disabled={busy}
                >
                  Sign out
                </button>
              </div>
            )}
          </div>
        ) : (
          <Link className="admin-public-link" href="/">
            Public site
          </Link>
        )}
      </header>

      {access === "checking" ? (
        <section className="admin-state" aria-live="polite">
          Checking administrator session...
        </section>
      ) : access === "signed-out" ? (
        <section className="admin-login-layout">
          <div className="admin-login-intro">
            <p className="admin-eyebrow">Restricted access</p>
            <h1>Administration is protected.</h1>
            <p>
              Use the shared MedNet sign-in to authenticate. Only accounts with
              the administrator role can enter this console.
            </p>
          </div>
          <div className="admin-login-form">
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <Link className="admin-submit" href="/sign-in?redirect=/admin">
              Go to secure sign in
            </Link>
            <Link className="admin-google-link" href="/">
              Return to public site
            </Link>
          </div>
        </section>
      ) : (
        <AdminWorkspace
          activeTab={activeTab}
          accounts={accounts}
          auditEvents={auditEvents}
          busy={busy}
          error={error}
          overview={overview}
          providers={providers}
          requests={requests}
          onChangeStatus={changeRecordStatus}
          onChangeRole={changeAccountRole}
          onDeleteAccount={deleteAccount}
          onChangeAccountPage={changeAccountPage}
          onCreate={createRecord}
          onOpenTab={openTab}
          onRefresh={() =>
            void refreshOverview().catch(() =>
              setError("The admin API could not be reached."),
            )
          }
        />
      )}
    </main>
  );
}

function AdminWorkspace({
  activeTab,
  accounts,
  auditEvents,
  busy,
  error,
  overview,
  providers,
  requests,
  onChangeStatus,
  onChangeRole,
  onDeleteAccount,
  onChangeAccountPage,
  onCreate,
  onOpenTab,
  onRefresh,
}: {
  activeTab: AdminTab;
  accounts: PlatformAccount[];
  auditEvents: AdminAuditEvent[];
  busy: boolean;
  error: string;
  overview: AdminOverview | null;
  providers: ProviderApplication[];
  requests: ServiceRequest[];
  onChangeStatus: (
    path: string,
    id: string,
    status: string,
    tab: Exclude<AdminTab, "overview" | "audit">,
  ) => Promise<void>;
  onChangeRole: (id: string, accountType: string) => Promise<void>;
  onDeleteAccount: (id: string) => Promise<void>;
  onChangeAccountPage: (page: number) => Promise<void>;
  onCreate: (
    event: FormEvent<HTMLFormElement>,
    path: string,
    tab: Exclude<AdminTab, "overview">,
  ) => Promise<void>;
  onOpenTab: (tab: AdminTab) => Promise<void>;
  onRefresh: () => void;
}) {
  const tabs: Array<{ key: AdminTab; label: string }> = [
    { key: "overview", label: "Overview" },
    { key: "providers", label: "Providers" },
    { key: "accounts", label: "Accounts" },
    { key: "requests", label: "Requests" },
    { key: "audit", label: "Audit" },
  ];
  const moduleStatus =
    activeTab === "overview"
      ? "CONNECTED"
      : overview?.modules.find((module) => module.key === moduleKeys[activeTab])
          ?.status;
  const workflowReady =
    moduleStatus === "CONNECTED" || moduleStatus === "PARTIAL";

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar" aria-label="Administration navigation">
        <div>
          <p className="admin-sidebar-label">Workspace</p>
          <nav className="admin-sidebar-nav">
            {tabs.map((tab) => (
              <button
                aria-current={activeTab === tab.key ? "page" : undefined}
                className={
                  activeTab === tab.key
                    ? "admin-sidebar-link admin-sidebar-link-active"
                    : "admin-sidebar-link"
                }
                key={tab.key}
                onClick={() => void onOpenTab(tab.key)}
                type="button"
              >
                <span className="admin-sidebar-icon" aria-hidden="true">
                  {tab.key === "overview"
                    ? "01"
                    : tab.key.slice(0, 2).toUpperCase()}
                </span>
                {tab.label}
              </button>
            ))}
          </nav>
        </div>
        <div className="admin-sidebar-footer">
          <Link href="/account">Account</Link>
          <Link href="/">Public site</Link>
        </div>
      </aside>
      <section className="admin-content">
        <div className="admin-page-heading">
          <div>
            <p className="admin-eyebrow">Operations</p>
            <h1>
              {activeTab === "overview"
                ? "System overview"
                : moduleLabels[moduleKeys[activeTab]]}
            </h1>
            <p>Backend status and non-clinical operational records.</p>
          </div>
          <button className="admin-refresh" type="button" onClick={onRefresh}>
            Refresh status
          </button>
        </div>

        {error && (
          <p className="admin-error" role="alert">
            {error}
          </p>
        )}

        {!overview ? (
          <div className="admin-state" aria-live="polite">
            Loading backend status...
          </div>
        ) : activeTab === "overview" ? (
          <>
            <div className="admin-status-strip">
              <div>
                <span className="admin-data-label">Spring Boot API</span>
                <strong className="admin-health-value">
                  <span className="admin-health-dot" aria-hidden="true" />
                  {overview.apiStatus}
                </strong>
              </div>
              <div>
                <span className="admin-data-label">API uptime</span>
                <strong>{Math.floor(overview.uptimeSeconds / 3600)}h</strong>
              </div>
              <div>
                <span className="admin-data-label">Last checked</span>
                <strong>{new Date(overview.checkedAt).toLocaleString()}</strong>
              </div>
            </div>
            {overview.counts && (
              <div className="admin-count-strip">
                <div>
                  <strong>{overview.counts.pendingProviders}</strong>
                  <span>Pending providers</span>
                </div>
                <div>
                  <strong>{overview.counts.activeAccounts}</strong>
                  <span>Active account records</span>
                </div>
                <div>
                  <strong>{overview.counts.openRequests}</strong>
                  <span>Open requests</span>
                </div>
              </div>
            )}
            <section className="admin-module-section">
              <div className="admin-section-heading">
                <div>
                  <p className="admin-eyebrow">Administration modules</p>
                  <h2>Workflow connectivity</h2>
                </div>
                <span className="admin-readonly-label">PostgreSQL-backed</span>
              </div>
              <div className="admin-module-list">
                {overview.modules.map((module) => (
                  <div className="admin-module-row" key={module.key}>
                    <span>{moduleLabels[module.key] ?? module.key}</span>
                    <span
                      className={
                        module.status === "CONNECTED"
                          ? "admin-module-status admin-module-ready"
                          : "admin-module-status"
                      }
                    >
                      {module.status === "CONNECTED"
                        ? "Connected"
                        : module.status === "PARTIAL"
                          ? "Registry only"
                          : "Configure PostgreSQL"}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          </>
        ) : !workflowReady ? (
          <section className="admin-not-configured">
            <p className="admin-eyebrow">Database connection required</p>
            <h2>PostgreSQL is not configured</h2>
            <p>
              Set <code>SPRING_DATASOURCE_URL</code>,{" "}
              <code>SPRING_DATASOURCE_USERNAME</code>, and
              <code> SPRING_DATASOURCE_PASSWORD</code> in LPAD. Flyway applies
              the schema on API startup.
            </p>
          </section>
        ) : activeTab === "providers" ? (
          <section className="admin-workflow-section">
            <p className="admin-workflow-note">
              Manual intake for provider applications. Credential references
              only; do not enter clinical information or upload documents here.
            </p>
            <form
              className="admin-record-form"
              onSubmit={(event) =>
                void onCreate(event, "providers", "providers")
              }
            >
              <label>
                Name
                <input name="displayName" maxLength={160} required />
              </label>
              <label>
                Email
                <input name="email" type="email" maxLength={254} required />
              </label>
              <label>
                Specialty
                <input name="specialty" maxLength={120} required />
              </label>
              <label>
                Credential reference
                <input name="credentialReference" maxLength={120} required />
              </label>
              <button className="admin-submit" type="submit" disabled={busy}>
                Add application
              </button>
            </form>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Provider</th>
                    <th>Specialty</th>
                    <th>Credential ref.</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {providers.map((provider) => (
                    <tr key={provider.id}>
                      <td>
                        <strong>{provider.displayName}</strong>
                        <small>{provider.email}</small>
                      </td>
                      <td>{provider.specialty}</td>
                      <td>{provider.credentialReference}</td>
                      <td>{provider.status}</td>
                      <td className="admin-row-actions">
                        {provider.status === "PENDING" && (
                          <>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void onChangeStatus(
                                  "providers",
                                  provider.id,
                                  "APPROVED",
                                  "providers",
                                )
                              }
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() =>
                                void onChangeStatus(
                                  "providers",
                                  provider.id,
                                  "REJECTED",
                                  "providers",
                                )
                              }
                            >
                              Reject
                            </button>
                          </>
                        )}
                        {provider.status === "APPROVED" && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              void onChangeStatus(
                                "providers",
                                provider.id,
                                "SUSPENDED",
                                "providers",
                              )
                            }
                          >
                            Suspend
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ) : activeTab === "accounts" ? (
          <section className="admin-workflow-section">
            <p className="admin-workflow-note">
              This is a non-clinical account registry. Verified accounts use
              their assigned role for access; only SUPER_ADMIN can change roles
              or delete accounts.
            </p>
            <form
              className="admin-record-form"
              onSubmit={(event) => void onCreate(event, "accounts", "accounts")}
            >
              <label>
                Email
                <input name="email" type="email" maxLength={254} required />
              </label>
              <label>
                Account type
                <select name="accountType" defaultValue="PATIENT">
                  <option value="PATIENT">Patient</option>
                  <option value="PROVIDER">Provider</option>
                </select>
              </label>
              <button className="admin-submit" type="submit" disabled={busy}>
                Add account record
              </button>
            </form>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Type</th>
                    <th>Verified</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <tr key={account.id}>
                      <td>{account.email}</td>
                      <td>{account.accountType}</td>
                      <td>{account.emailVerified ? "Yes" : "Pending"}</td>
                      <td>{account.status}</td>
                      <td>
                        {new Date(account.createdAt).toLocaleDateString()}
                      </td>
                      <td className="admin-row-actions">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            void onChangeStatus(
                              "accounts",
                              account.id,
                              account.status === "ACTIVE"
                                ? "SUSPENDED"
                                : "ACTIVE",
                              "accounts",
                            )
                          }
                        >
                          {account.status === "ACTIVE"
                            ? "Suspend record"
                            : "Reactivate record"}
                        </button>
                        <select
                          aria-label={`Change role for ${account.email}`}
                          value={account.accountType}
                          disabled={busy}
                          onChange={(event) =>
                            void onChangeRole(account.id, event.target.value)
                          }
                        >
                          <option value="PATIENT">Patient</option>
                          <option value="PROVIDER">Provider</option>
                          <option value="ADMIN">Admin</option>
                          <option value="SUPER_ADMIN">Super admin</option>
                        </select>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void onDeleteAccount(account.id)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="admin-pagination">
              <span>{accountPage.totalElements} account records</span>
              <div>
                <button
                  type="button"
                  disabled={busy || accountPage.number === 0}
                  onClick={() => void onChangeAccountPage(0)}
                >
                  First page
                </button>
                <span>
                  Page {accountPage.number + 1} of{" "}
                  {Math.max(1, accountPage.totalPages)}
                </span>
                <button
                  type="button"
                  disabled={
                    busy || accountPage.number + 1 >= accountPage.totalPages
                  }
                  onClick={() => void onChangeAccountPage(accountPage.number + 1)}
                >
                  Next page
                </button>
              </div>
            </div>
          </section>
        ) : activeTab === "requests" ? (
          <section className="admin-workflow-section">
            <p className="admin-workflow-note">
              Admin-entered operational references only. Patient submissions and
              notifications are not connected yet; do not enter symptoms, test
              results, or other clinical details.
            </p>
            <form
              className="admin-record-form"
              onSubmit={(event) => void onCreate(event, "requests", "requests")}
            >
              <label>
                Reference
                <input name="referenceId" maxLength={80} required />
              </label>
              <label>
                Request type
                <select name="requestType" defaultValue="APPOINTMENT">
                  <option value="APPOINTMENT">Appointment</option>
                  <option value="HOME_CARE">Home care</option>
                  <option value="LABORATORY">Laboratory</option>
                </select>
              </label>
              <label>
                Requester email
                <input
                  name="requesterEmail"
                  type="email"
                  maxLength={254}
                  required
                />
              </label>
              <button className="admin-submit" type="submit" disabled={busy}>
                Add request record
              </button>
            </form>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Reference</th>
                    <th>Type</th>
                    <th>Requester</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.map((request) => (
                    <tr key={request.id}>
                      <td>{request.referenceId}</td>
                      <td>{request.requestType}</td>
                      <td>{request.requesterEmail}</td>
                      <td>{request.status}</td>
                      <td className="admin-row-actions">
                        {request.status === "OPEN" && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              void onChangeStatus(
                                "requests",
                                request.id,
                                "IN_PROGRESS",
                                "requests",
                              )
                            }
                          >
                            Start
                          </button>
                        )}
                        {request.status === "IN_PROGRESS" && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              void onChangeStatus(
                                "requests",
                                request.id,
                                "RESOLVED",
                                "requests",
                              )
                            }
                          >
                            Resolve
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ) : (
          <section className="admin-workflow-section">
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Resource</th>
                    <th>Reference</th>
                  </tr>
                </thead>
                <tbody>
                  {auditEvents.map((event) => (
                    <tr key={event.id}>
                      <td>{new Date(event.createdAt).toLocaleString()}</td>
                      <td>{event.actorEmail}</td>
                      <td>{event.action}</td>
                      <td>{event.resourceType}</td>
                      <td>{event.resourceId}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </section>
    </div>
  );
}
