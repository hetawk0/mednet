"use client";

import { Fragment, type FormEvent, useEffect, useState } from "react";
import Link from "next/link";

type AccessState =
  | "checking"
  | "signed-out"
  | "forbidden"
  | "unavailable"
  | "signed-in";

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
  publicId: string;
  displayName: string | null;
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

async function responseErrorMessage(
  response: Response,
  fallback: string,
): Promise<string> {
  const payload: unknown = await response.json().catch(() => null);
  if (typeof payload !== "object" || payload === null) return fallback;
  const body = payload as Record<string, unknown>;
  const message =
    typeof body.detail === "string"
      ? body.detail
      : typeof body.message === "string"
        ? body.message
        : null;
  return message?.trim() || fallback;
}

const moduleLabels: Record<string, string> = {
  providerReview: "Provider applications",
  accountSupport: "User management",
  serviceRequests: "Service request queue",
  auditTrail: "Administrative audit trail",
  messaging: "Messaging",
  medication: "Medication",
  vitals: "Vitals",
  notifications: "Notifications",
  homeCare: "Home care",
  laboratory: "Laboratory",
  textConsultations: "Text consultations",
  virtualConsultation: "Video and voice consultations",
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
  const [accountSearch, setAccountSearch] = useState("");
  const [accountStatus, setAccountStatus] = useState("");
  const [accountTypeFilter, setAccountTypeFilter] = useState("");
  const [accountVerificationFilter, setAccountVerificationFilter] = useState("");
  const [accountPageSize, setAccountPageSize] = useState(25);
  const [accountPasswordMode, setAccountPasswordMode] = useState<"manual" | "generated">("manual");
  const [createdPassword, setCreatedPassword] = useState("");
  const [editingAccountId, setEditingAccountId] = useState("");
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

  async function searchAccounts(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const search = String(formData.get("search") ?? "").trim();
    const status = String(formData.get("status") ?? "");
    const accountType = String(formData.get("accountType") ?? "");
    const emailVerified = String(formData.get("emailVerified") ?? "");
    setAccountSearch(search);
    setAccountStatus(status);
    setAccountTypeFilter(accountType);
    setAccountVerificationFilter(emailVerified);
    setBusy(true);
    setError("");
    try {
      await reloadTab("accounts", 0, { search, status, accountType, emailVerified });
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : "Could not search accounts.",
      );
    } finally {
      setBusy(false);
    }
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
        if (response.status === 401) {
          setError(
            "Your administrator session is no longer active. Sign in again to continue.",
          );
          setAccess("signed-out");
          return;
        }
        if (response.status === 403) {
          setError(
            "This session does not have administrator access. Sign in with an ADMIN or SUPER_ADMIN account.",
          );
          setAccess("forbidden");
          return;
        }
        if (!response.ok) {
          setError(
            `The MedNet API could not verify your administrator session (${response.status}).`,
          );
          setAccess("unavailable");
          return;
        }

        const currentSession = (await response.json()) as AdminSession;
        if (
          currentSession.role !== "ADMIN" &&
          currentSession.role !== "SUPER_ADMIN"
        ) {
          setError(
            "This session does not have administrator access. Sign in with an ADMIN or SUPER_ADMIN account.",
          );
          setAccess("forbidden");
          return;
        }
        const overviewResponse = await fetch("/api/v1/admin/overview", {
          cache: "no-store",
          credentials: "same-origin",
        });
        if (overviewResponse.status === 401) {
          setError(
            "Your administrator session is no longer active. Sign in again to continue.",
          );
          setAccess("signed-out");
          return;
        }
        if (overviewResponse.status === 403) {
          setError(
            "Your account is signed in, but does not have permission to open the administration console.",
          );
          setAccess("forbidden");
          return;
        }
        if (!overviewResponse.ok) {
          setError(
            `Your administrator session is valid, but the MedNet API could not load the console (${overviewResponse.status}).`,
          );
          setSession(currentSession);
          setAccess("unavailable");
          return;
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
        setAccess("unavailable");
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

  async function reloadTab(
    tab: Exclude<AdminTab, "overview">,
    requestedPage = 0,
    filters: {
      search?: string;
      status?: string;
      accountType?: string;
      emailVerified?: string;
      pageSize?: number;
    } = {},
  ) {
    const endpoint = tab === "audit" ? "audit" : tab;
    const accountQuery = new URLSearchParams({
      page: String(requestedPage),
      size: String(filters.pageSize ?? accountPageSize),
    });
    const search = filters.search ?? accountSearch;
    const status = filters.status ?? accountStatus;
    const accountType = filters.accountType ?? accountTypeFilter;
    const emailVerified = filters.emailVerified ?? accountVerificationFilter;
    if (search.trim()) accountQuery.set("search", search.trim());
    if (status) accountQuery.set("status", status);
    if (accountType) accountQuery.set("accountType", accountType);
    if (emailVerified) accountQuery.set("emailVerified", emailVerified);
    const query =
      tab === "accounts" ? `?${accountQuery.toString()}` : "";
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
    if (moduleStatus !== "CONNECTED" && moduleStatus !== "PARTIAL") return;

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
    method: "POST" | "PATCH" | "PUT",
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
    if (response.status === 401)
      throw new Error("Your administrator session expired. Sign in again.");
    if (response.status === 403)
      throw new Error(
        path.endsWith("/role")
          ? "Only a SUPER_ADMIN can change account roles."
          : "Your administrator account does not have permission for this action.",
      );
    if (response.status === 409) {
      const fallback =
        method === "POST" && path === "accounts"
          ? "An account with this email already exists."
          : "This account conflicts with an existing account or protected record.";
      throw new Error(await responseErrorMessage(response, fallback));
    }
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
    let generatedPassword = "";
    if (path === "accounts") {
      if (accountPasswordMode === "generated") {
        const randomBytes = crypto.getRandomValues(new Uint8Array(24));
        generatedPassword = btoa(String.fromCharCode(...randomBytes))
          .replaceAll("+", "-")
          .replaceAll("/", "_")
          .replaceAll("=", "");
        payload.password = generatedPassword;
      }
      delete payload.passwordMode;
    }

    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(path, "POST", payload);
      if (path === "accounts") {
        setCreatedPassword(generatedPassword);
        setAccountPasswordMode("manual");
      }
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

  async function changeAccountVerification(id: string, emailVerified: boolean) {
    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(`accounts/${id}/verification`, "PATCH", { emailVerified });
      await reloadTab("accounts");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not update verification status.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function updateAccount(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload: Record<string, string> = {};
    new FormData(form).forEach((value, key) => {
      const field = String(value);
      if (key !== "password" || field.trim()) payload[key] = field;
    });
    setBusy(true);
    setError("");
    try {
      await mutateWorkflow(`accounts/${id}`, "PUT", payload);
      setEditingAccountId("");
      await reloadTab("accounts");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not update this account.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function deleteAccount(id: string) {
    const account = accounts.find((candidate) => candidate.id === id);
    if (
      !account ||
      !window.confirm(
        `Permanently delete ${account.email}? Accounts linked to clinical or care records cannot be deleted.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError("");
    try {
      const csrfResponse = await fetch("/api/v1/auth/csrf", {
        credentials: "same-origin",
      });
      if (!csrfResponse.ok)
        throw new Error("Could not initialize the secure request.");
      const csrf = (await csrfResponse.json()) as CsrfResponse;
      const response = await fetch(`/api/v1/admin/accounts/${id}`, {
        method: "DELETE",
        credentials: "same-origin",
        headers: { [csrf.headerName]: csrf.token },
      });
      if (response.status === 401)
        throw new Error("Your administrator session expired. Sign in again.");
      if (response.status === 403)
        throw new Error("Only a SUPER_ADMIN can delete accounts.");
      if (response.status === 409)
        throw new Error(
          await responseErrorMessage(
            response,
            "This account conflicts with an existing account or protected record.",
          ),
        );
      if (!response.ok)
        throw new Error(
          `Account deletion failed (${response.status}).`,
        );
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

  async function changeAccountPage(page: number, size = accountPageSize) {
    setBusy(true);
    try {
      if (size !== accountPageSize) setAccountPageSize(size);
      await reloadTab("accounts", page, { pageSize: size });
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
              aria-label={`Administrator menu for ${session.email}`}
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
                <svg
                  viewBox="0 0 20 20"
                  fill="none"
                  aria-hidden="true"
                  className={userMenuOpen ? "admin-menu-chevron-open" : ""}
                >
                  <path
                    d="m5.5 7.5 4.5 4.5 4.5-4.5"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </span>
            </button>
            {userMenuOpen && (
              <div className="admin-user-menu" role="menu">
                <Link
                  href="/account"
                  role="menuitem"
                  onClick={() => setUserMenuOpen(false)}
                >
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
              Your administrator session is no longer active. Sign in again
              with an ADMIN or SUPER_ADMIN account to continue.
            </p>
          </div>
          <div className="admin-access-card">
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <span className="admin-access-card-label">Administrator access</span>
            <h2>Continue to your workspace</h2>
            <p>
              Sign in securely to return to platform operations. Your
              administrator permissions will be checked automatically.
            </p>
            <Link
              className="admin-access-button"
              href="/sign-in?redirect=%2Fadmin"
            >
              <span>Sign in to administration</span>
              <span aria-hidden="true">→</span>
            </Link>
            <Link className="admin-access-secondary" href="/">
              Return to public site
            </Link>
          </div>
        </section>
      ) : access === "forbidden" ? (
        <section className="admin-login-layout">
          <div className="admin-login-intro">
            <p className="admin-eyebrow">Administrator access</p>
            <h1>This account cannot open the console.</h1>
            <p>
              The signed-in account does not have an administrator role. Both
              ADMIN and SUPER_ADMIN accounts are supported.
            </p>
          </div>
          <div className="admin-access-card">
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <span className="admin-access-card-label">Access not granted</span>
            <h2>Check the account you used</h2>
            <p>
              Sign out of the current account, then sign in with an
              administrator account.
            </p>
            <Link
              className="admin-access-button"
              href="/sign-in?redirect=%2Fadmin"
            >
              <span>Choose another account</span>
              <span aria-hidden="true">→</span>
            </Link>
            <Link className="admin-access-secondary" href="/">
              Return to public site
            </Link>
          </div>
        </section>
      ) : access === "unavailable" ? (
        <section className="admin-login-layout">
          <div className="admin-login-intro">
            <p className="admin-eyebrow">Connection issue</p>
            <h1>Your admin session is being checked.</h1>
            <p>
              We could not reach the MedNet administration API. This does not
              mean your account has lost its administrator access.
            </p>
          </div>
          <div className="admin-access-card">
            {error && (
              <p className="admin-error" role="alert">
                {error}
              </p>
            )}
            <span className="admin-access-card-label">MedNet services</span>
            <h2>Try the connection again</h2>
            <p>
              Your ADMIN or SUPER_ADMIN access will be verified when the API is
              available.
            </p>
            <button
              className="admin-access-button"
              type="button"
              onClick={() => window.location.reload()}
            >
              <span>Retry connection</span>
              <span aria-hidden="true">↻</span>
            </button>
            <Link className="admin-access-secondary" href="/">
              Return to public site
            </Link>
          </div>
        </section>
      ) : (
        <AdminWorkspace
          activeTab={activeTab}
          accountPage={accountPage}
          accountSearch={accountSearch}
          accountStatus={accountStatus}
          accountTypeFilter={accountTypeFilter}
          accountVerificationFilter={accountVerificationFilter}
          accountPageSize={accountPageSize}
          accountPasswordMode={accountPasswordMode}
          createdPassword={createdPassword}
          editingAccountId={editingAccountId}
          currentEmail={session?.email ?? ""}
          isSuperAdmin={session?.role === "SUPER_ADMIN"}
          accounts={accounts}
          auditEvents={auditEvents}
          busy={busy}
          error={error}
          overview={overview}
          providers={providers}
          requests={requests}
          onChangeStatus={changeRecordStatus}
          onChangeVerification={changeAccountVerification}
          onDeleteAccount={deleteAccount}
          onChangeAccountPage={changeAccountPage}
          onSetPasswordMode={setAccountPasswordMode}
          onClearCreatedPassword={() => setCreatedPassword("")}
          onCopyPassword={async (password) => {
            try {
              await navigator.clipboard.writeText(password);
            } catch {
              setError("Could not copy the generated password.");
            }
          }}
          onSetEditingAccountId={setEditingAccountId}
          onUpdateAccount={updateAccount}
          onSearchAccounts={searchAccounts}
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
  accountPage,
  accountSearch,
  accountStatus,
  accountTypeFilter,
  accountVerificationFilter,
  accountPageSize,
  accountPasswordMode,
  createdPassword,
  editingAccountId,
  currentEmail,
  isSuperAdmin,
  accounts,
  auditEvents,
  busy,
  error,
  overview,
  providers,
  requests,
  onChangeStatus,
  onChangeVerification,
  onDeleteAccount,
  onChangeAccountPage,
  onSetPasswordMode,
  onClearCreatedPassword,
  onCopyPassword,
  onSetEditingAccountId,
  onUpdateAccount,
  onSearchAccounts,
  onCreate,
  onOpenTab,
  onRefresh,
}: {
  activeTab: AdminTab;
  accountPage: { number: number; totalPages: number; totalElements: number };
  accountSearch: string;
  accountStatus: string;
  accountTypeFilter: string;
  accountVerificationFilter: string;
  accountPageSize: number;
  accountPasswordMode: "manual" | "generated";
  createdPassword: string;
  editingAccountId: string;
  currentEmail: string;
  isSuperAdmin: boolean;
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
  onChangeVerification: (id: string, verified: boolean) => Promise<void>;
  onDeleteAccount: (id: string) => Promise<void>;
  onChangeAccountPage: (page: number, size?: number) => Promise<void>;
  onSetPasswordMode: (mode: "manual" | "generated") => void;
  onClearCreatedPassword: () => void;
  onCopyPassword: (password: string) => Promise<void>;
  onSetEditingAccountId: (id: string) => void;
  onUpdateAccount: (event: FormEvent<HTMLFormElement>, id: string) => Promise<void>;
  onSearchAccounts: (event: FormEvent<HTMLFormElement>) => void;
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
    { key: "accounts", label: "User management" },
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
                    <span className={`admin-module-status ${
                      module.status === "CONNECTED"
                        ? "admin-module-ready"
                        : module.status === "DISABLED"
                          ? "admin-module-disabled"
                          : module.status === "PARTIAL"
                            ? "admin-module-partial"
                            : "admin-module-unconfigured"
                    }`}>
                      {module.status === "CONNECTED"
                        ? "Connected"
                        : module.status === "PARTIAL"
                          ? "Partially available"
                          : module.status === "DISABLED"
                            ? "Not implemented"
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
              Manage platform accounts here. New account records must complete
              normal email verification before sign-in. ADMIN can suspend or
              reactivate accounts; only SUPER_ADMIN can edit account details,
              verification, roles, and deletion. Public account IDs are short,
              type-prefixed identifiers; internal database IDs are not shown.
              Linked records prevent account-type changes or deletion, but do
              not prevent editing other account details. The environment-
              configured administrator must be changed through deployment
              settings.
            </p>
            <form
              className="admin-record-form"
              onSubmit={(event) => void onCreate(event, "accounts", "accounts")}
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
                Account type
                <select name="accountType" defaultValue="PATIENT">
                  <option value="PATIENT">Patient</option>
                  <option value="PROVIDER">Provider</option>
                  <option value="HOME_CARE">Home care staff</option>
                  <option value="LABORATORY">Laboratory staff</option>
                </select>
              </label>
              <label>
                Initial password
                <select
                  name="passwordMode"
                  value={accountPasswordMode}
                  onChange={(event) => {
                    onClearCreatedPassword();
                    onSetPasswordMode(event.target.value as "manual" | "generated");
                  }}
                >
                  <option value="manual">Enter manually</option>
                  <option value="generated">Generate securely</option>
                </select>
              </label>
              {accountPasswordMode === "manual" && (
                <label>
                  Password (12–72 characters)
                  <input
                    name="password"
                    type="password"
                    minLength={12}
                    maxLength={72}
                    autoComplete="new-password"
                    required
                  />
                </label>
              )}
              <button className="admin-submit" type="submit" disabled={busy}>
                Create account
              </button>
            </form>
            {createdPassword && (
              <div className="admin-generated-password" role="status">
                <div>
                  <strong>Save this generated password now</strong>
                  <span>It is shown only once and will not be available again.</span>
                </div>
                <code>{createdPassword}</code>
                <button
                  type="button"
                  onClick={() => void onCopyPassword(createdPassword)}
                >
                  Copy password
                </button>
              </div>
            )}
            <form className="admin-account-filters" onSubmit={onSearchAccounts}>
              <label>
                Search by name, email, or public ID
                <input
                  name="search"
                  type="search"
                  maxLength={254}
                  defaultValue={accountSearch}
                  placeholder="Name, email, or PT-..."
                />
              </label>
              <label>
                Account status
                <select name="status" defaultValue={accountStatus}>
                  <option value="">All statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="SUSPENDED">Suspended</option>
                </select>
              </label>
              <label>
                Account type
                <select name="accountType" defaultValue={accountTypeFilter}>
                  <option value="">All types</option>
                  <option value="PATIENT">Patient</option>
                  <option value="PROVIDER">Provider</option>
                  <option value="ADMIN">Admin</option>
                  <option value="SUPER_ADMIN">Super admin</option>
                  <option value="HOME_CARE">Home care</option>
                  <option value="LABORATORY">Laboratory</option>
                </select>
              </label>
              <label>
                Verification
                <select
                  name="emailVerified"
                  defaultValue={accountVerificationFilter}
                >
                  <option value="">All</option>
                  <option value="true">Verified</option>
                  <option value="false">Pending</option>
                </select>
              </label>
              <button className="admin-submit" type="submit" disabled={busy}>
                Search accounts
              </button>
            </form>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Public ID</th>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Type</th>
                    <th>Verified</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.length === 0 ? (
                    <tr>
                      <td colSpan={8}>No accounts match these filters.</td>
                    </tr>
                  ) : (
                    accounts.map((account) => (
                      <Fragment key={account.id}>
                        <tr>
                          <td><code className="admin-account-id">{account.publicId}</code></td>
                          <td>{account.displayName || "—"}</td>
                          <td>{account.email}</td>
                          <td>{account.accountType}</td>
                          <td>{account.emailVerified ? "Verified" : "Pending"}</td>
                          <td>{account.status}</td>
                          <td>{new Date(account.createdAt).toLocaleDateString()}</td>
                          <td className="admin-row-actions">
                            <button
                              type="button"
                              disabled={busy || account.email.toLowerCase() === currentEmail.toLowerCase()}
                              onClick={() =>
                                void onChangeStatus(
                                  "accounts",
                                  account.id,
                                  account.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE",
                                  "accounts",
                                )
                              }
                            >
                              {account.status === "ACTIVE" ? "Suspend" : "Reactivate"}
                            </button>
                            {isSuperAdmin && (
                              <>
                                <button
                                  type="button"
                                  disabled={busy || account.email.toLowerCase() === currentEmail.toLowerCase()}
                                  onClick={() =>
                                    onSetEditingAccountId(
                                      editingAccountId === account.id ? "" : account.id,
                                    )
                                  }
                                >
                                  {editingAccountId === account.id ? "Cancel edit" : "Edit"}
                                </button>
                                <button
                                  type="button"
                                  disabled={busy || account.email.toLowerCase() === currentEmail.toLowerCase()}
                                  onClick={() =>
                                    void onChangeVerification(account.id, !account.emailVerified)
                                  }
                                >
                                  Mark {account.emailVerified ? "pending" : "verified"}
                                </button>
                                <button
                                  type="button"
                                  disabled={busy || account.email.toLowerCase() === currentEmail.toLowerCase()}
                                  onClick={() => void onDeleteAccount(account.id)}
                                >
                                  Delete
                                </button>
                              </>
                            )}
                          </td>
                        </tr>
                        {isSuperAdmin && editingAccountId === account.id && (
                          <tr>
                            <td colSpan={8}>
                              <form
                                className="admin-account-edit-form"
                                onSubmit={(event) => void onUpdateAccount(event, account.id)}
                              >
                                <label>
                                  Name
                                  <input name="displayName" defaultValue={account.displayName ?? ""} maxLength={160} required />
                                </label>
                                <label>
                                  Email
                                  <input name="email" type="email" defaultValue={account.email} maxLength={254} required />
                                </label>
                                <label>
                                  Account type
                                  <select name="accountType" defaultValue={account.accountType}>
                                    <option value="PATIENT">Patient</option>
                                    <option value="PROVIDER">Provider</option>
                                    <option value="ADMIN">Admin</option>
                                    <option value="SUPER_ADMIN">Super admin</option>
                                    <option value="HOME_CARE">Home care staff</option>
                                    <option value="LABORATORY">Laboratory staff</option>
                                  </select>
                                </label>
                                <label>
                                  Replace password (optional)
                                  <input name="password" type="password" minLength={12} maxLength={72} autoComplete="new-password" />
                                </label>
                                <button className="admin-submit" type="submit" disabled={busy}>Save changes</button>
                              </form>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <div className="admin-pagination">
              <span>
                {accountPage.totalElements === 0
                  ? "No account records"
                  : `${accountPage.number * accountPageSize + 1}–${Math.min(
                      (accountPage.number + 1) * accountPageSize,
                      accountPage.totalElements,
                    )} of ${accountPage.totalElements} accounts`}
              </span>
              <div>
                <label className="admin-page-size">
                  Rows
                  <select
                    value={accountPageSize}
                    disabled={busy}
                    onChange={(event) =>
                      void onChangeAccountPage(0, Number(event.target.value))
                    }
                  >
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </label>
                <button
                  type="button"
                  disabled={busy || accountPage.number <= 0}
                  onClick={() => void onChangeAccountPage(0)}
                >
                  First
                </button>
                <button
                  type="button"
                  disabled={busy || accountPage.number <= 0}
                  onClick={() => void onChangeAccountPage(accountPage.number - 1)}
                >
                  Previous
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
                  onClick={() =>
                    void onChangeAccountPage(accountPage.number + 1)
                  }
                >
                  Next page
                </button>
                <button
                type="button"
                disabled={
                  busy ||
                  accountPage.totalPages === 0 ||
                  accountPage.number + 1 >= accountPage.totalPages
                }
                onClick={() => void onChangeAccountPage(accountPage.totalPages - 1)}
                >
                Last
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
