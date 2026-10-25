import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationPreferencesPanel } from "./notification-preferences";

const api = vi.hoisted(() => ({
  emailPreferences: vi.fn(),
  updateEmailPreferences: vi.fn(),
}));

vi.mock("./notification-api", () => ({ notificationApi: api }));

beforeEach(() => {
  api.emailPreferences.mockReset();
  api.updateEmailPreferences.mockReset();
});

describe("NotificationPreferencesPanel", () => {
  it("loads and updates the authenticated user's email preference", async () => {
    api.emailPreferences.mockResolvedValue({ emailEnabled: true, emailVerified: true });
    api.updateEmailPreferences.mockResolvedValue({ emailEnabled: false, emailVerified: true });
    render(<NotificationPreferencesPanel />);

    const toggle = await screen.findByRole("checkbox", { name: "Send me email notifications" });
    expect(toggle).toBeChecked();
    fireEvent.click(toggle);

    expect(await screen.findByText("Email notification preference saved.")).toBeInTheDocument();
    expect(api.updateEmailPreferences).toHaveBeenCalledWith(false);
    expect(toggle).not.toBeChecked();
  });

  it("discloses the verified-email requirement and retries loading failures", async () => {
    api.emailPreferences
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({ emailEnabled: true, emailVerified: false });
    render(<NotificationPreferencesPanel />);

    expect(await screen.findByText("Email preferences could not be loaded.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText(/only after your sign-in email is verified/)).toBeInTheDocument();
    expect(api.emailPreferences).toHaveBeenCalledTimes(2);
  });
});
