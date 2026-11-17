import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApplicantDashboard } from "./application";
import { onboardingStates } from "./onboarding-steps";
import type { ProviderApplication } from "./provider-api";

const posts: unknown[] = [];
const notificationQueries: string[] = [];
let applyResponse: { status: number; body: unknown } = { status: 200, body: {} };
let updates: Array<Record<string, unknown>> = [];

const decision = (title: string, readAt: string | null = null) => ({
  id: `n-${title.length}`,
  eventType: "PROVIDER_APPLICATION",
  title,
  resourceType: "provider",
  resourceId: "app-1",
  createdAt: "2026-11-12T09:00:00Z",
  readAt,
});

beforeEach(() => {
  posts.length = 0;
  notificationQueries.length = 0;
  applyResponse = { status: 200, body: {} };
  updates = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/auth/csrf")) return Response.json({ headerName: "X-XSRF-TOKEN", token: "t" });
      if (url.includes("/notifications?") && (init?.method ?? "GET") === "GET") {
        notificationQueries.push(url.split("?")[1]);
        return Response.json({ content: updates, totalElements: updates.length, totalPages: 1, number: 0, size: 10 });
      }
      const read = url.match(/\/notifications\/([^/]+)\/read$/);
      if (read && init?.method === "PATCH") {
        return Response.json({ ...updates.find((item) => item.id === read[1]), readAt: "2026-11-13T09:00:00Z" });
      }
      if (url.endsWith("/providers/applications") && init?.method === "POST") {
        posts.push(JSON.parse(String(init.body)));
        return Response.json(applyResponse.body, { status: applyResponse.status });
      }
      throw new Error(`Unexpected request ${init?.method ?? "GET"} ${url}`);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

const application = (status: string): ProviderApplication => ({
  id: "app-1",
  displayName: "Dr. Abena Sirleaf",
  specialty: "Family medicine",
  credentialReference: "LMDC-2041",
  status,
  createdAt: "2026-11-10T09:00:00Z",
  reviewedAt: status === "PENDING" ? null : "2026-11-12T09:00:00Z",
});

function renderDashboard(app: ProviderApplication | null, providerAccount = true) {
  const onApplicationChanged = vi.fn();
  const onSignOut = vi.fn();
  render(
    <ApplicantDashboard
      application={app}
      providerAccount={providerAccount}
      onApplicationChanged={onApplicationChanged}
      onSignOut={onSignOut}
      signOutFailed={false}
    />,
  );
  return { onApplicationChanged, onSignOut };
}

function stepText(name: RegExp) {
  return within(screen.getByRole("list", { name: "Provider sign-up steps" })).getByText(name).closest("li")!;
}

describe("onboardingStates", () => {
  it("marks earlier steps complete, the current step current and a review outcome needing action", () => {
    expect(onboardingStates("details")).toEqual({ account: "complete", email: "complete", details: "current", review: "upcoming" });
    expect(onboardingStates("details", true).review).toBe("attention");
    expect(Object.values(onboardingStates("done"))).toEqual(["complete", "complete", "complete", "complete"]);
  });
});

describe("ApplicantDashboard", () => {
  it("asks a new provider sign-up for professional details and submits one application", async () => {
    const { onApplicationChanged } = renderDashboard(null);
    expect(screen.getByRole("heading", { name: "Submit your professional details" })).toBeInTheDocument();
    expect(stepText(/Submit your professional details/)).toHaveAttribute("aria-current", "step");
    expect(screen.queryByRole("link", { name: "Go to the patient workspace" })).not.toBeInTheDocument();

    const form = screen.getByRole("form", { name: "Professional details" });
    await userEvent.click(within(form).getByRole("button", { name: "Submit application" }));
    // The form focuses the first invalid field on the next animation frame.
    await waitFor(() => expect(within(form).getByRole("textbox", { name: /Display name/ })).toHaveFocus());
    expect(posts).toEqual([]);

    await userEvent.type(within(form).getByRole("textbox", { name: /Display name/ }), "Dr. Abena Sirleaf");
    await userEvent.type(within(form).getByRole("textbox", { name: /Specialty/ }), "Family medicine");
    await userEvent.type(within(form).getByRole("textbox", { name: /Licence or registration reference/ }), "LMDC-2041");
    await userEvent.click(within(form).getByRole("button", { name: "Submit application" }));

    await waitFor(() => expect(onApplicationChanged).toHaveBeenCalledTimes(1));
    expect(posts).toEqual([{ displayName: "Dr. Abena Sirleaf", specialty: "Family medicine", credentialReference: "LMDC-2041" }]);
  });

  it("resumes the existing application when the API reports one was already submitted", async () => {
    applyResponse = { status: 409, body: { success: false, error: { code: "CONFLICT", message: "A provider application already exists for this account" } } };
    const { onApplicationChanged } = renderDashboard(null);
    const form = screen.getByRole("form", { name: "Professional details" });
    await userEvent.type(within(form).getByRole("textbox", { name: /Display name/ }), "Dr. Abena Sirleaf");
    await userEvent.type(within(form).getByRole("textbox", { name: /Specialty/ }), "Family medicine");
    await userEvent.type(within(form).getByRole("textbox", { name: /Licence or registration reference/ }), "LMDC-2041");
    await userEvent.click(within(form).getByRole("button", { name: "Submit application" }));
    await waitFor(() => expect(onApplicationChanged).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows a pending application under review with a status check and no clinical actions", async () => {
    const { onApplicationChanged } = renderDashboard(application("PENDING"));
    expect(screen.getByRole("heading", { name: "Your application is under review" })).toBeInTheDocument();
    expect(stepText(/Administrator review/)).toHaveAttribute("aria-current", "step");
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /schedule|appointments|patients/i })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Check status" }));
    expect(onApplicationChanged).toHaveBeenCalledTimes(1);
  });

  it("shows review decisions from the notification feed on the dashboard and marks them read", async () => {
    updates = [decision("Your provider application was not approved")];
    renderDashboard(application("REJECTED"));
    const list = await screen.findByRole("list", { name: "Application updates" });
    expect(within(list).getByText("Your provider application was not approved")).toBeInTheDocument();
    expect(notificationQueries).toEqual(["page=0&size=10&eventType=PROVIDER_APPLICATION"]);
    await userEvent.click(within(list).getByRole("button", { name: "Mark as read" }));
    await waitFor(() => expect(within(list).queryByRole("button", { name: "Mark as read" })).not.toBeInTheDocument());
    expect(within(list).getByText(/· Read$/)).toBeInTheDocument();
  });

  it("explains that a decision will appear once the application is reviewed", async () => {
    renderDashboard(application("PENDING"));
    expect(await screen.findByText("No decisions yet.")).toBeInTheDocument();
  });

  it("does not load application updates before an application exists", () => {
    renderDashboard(null);
    expect(screen.queryByRole("heading", { name: "Application updates" })).not.toBeInTheDocument();
    expect(notificationQueries).toEqual([]);
  });

  it("explains a rejection without inventing a reason, and offers a corrected application", () => {
    renderDashboard(application("REJECTED"));
    expect(screen.getByRole("heading", { name: "Your application was not approved" })).toBeInTheDocument();
    expect(screen.getByText(/did not approve it\. No reason is recorded with the decision\./)).toBeInTheDocument();
    expect(screen.getByText(/If you believe the decision is wrong, contact a MedNet administrator\./)).toBeInTheDocument();
    expect(stepText(/Administrator review/)).toHaveTextContent("needs attention");
    expect(screen.getByRole("form", { name: "Submit corrected details" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit corrected application" })).toBeInTheDocument();
  });

  it("offers no new application while suspended", () => {
    renderDashboard(application("SUSPENDED"));
    expect(screen.getByRole("heading", { name: "Your provider listing is suspended" })).toBeInTheDocument();
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
  });

  it("asks an applicant approved after signing in to sign in again", async () => {
    const { onSignOut } = renderDashboard(application("APPROVED"));
    expect(screen.getByRole("heading", { name: "Sign in again to open your doctor workspace" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(onSignOut).toHaveBeenCalledTimes(1);
  });

  it("keeps the patient workspace link for patients who applied", () => {
    renderDashboard(application("PENDING"), false);
    expect(screen.getByRole("link", { name: "Go to the patient workspace" })).toHaveAttribute("href", "/patient");
  });
});
