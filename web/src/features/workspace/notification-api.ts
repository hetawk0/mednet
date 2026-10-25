import { apiRequest } from "@/lib/api";

export type EmailNotificationPreferences = {
  emailEnabled: boolean;
  emailVerified: boolean;
};

export const notificationApi = {
  emailPreferences: (signal?: AbortSignal) =>
    apiRequest<EmailNotificationPreferences>("/notifications/preferences", { signal }),
  updateEmailPreferences: (emailEnabled: boolean) =>
    apiRequest<EmailNotificationPreferences>("/notifications/preferences", {
      method: "PATCH",
      body: { emailEnabled },
    }),
};
