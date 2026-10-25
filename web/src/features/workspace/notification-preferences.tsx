"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Card, CardHeader } from "@/components/ui/card";
import { LoadingRows, ErrorRow } from "./states";
import { notificationApi } from "./notification-api";
import { actionError, useResource } from "./use-resource";

export function NotificationPreferencesPanel() {
  const { resource, reload, replace } = useResource(
    (signal) => notificationApi.emailPreferences(signal),
  );
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: "success" | "critical"; message: string } | null>(null);

  async function update(enabled: boolean) {
    setSaving(true);
    setFeedback(null);
    try {
      replace(await notificationApi.updateEmailPreferences(enabled));
      setFeedback({ tone: "success", message: "Email notification preference saved." });
    } catch (error) {
      setFeedback({
        tone: "critical",
        message: actionError(error, "Your email notification preference could not be saved."),
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card>
      <CardHeader eyebrow="Notifications" title="Email preferences" titleAs="h3" />
      <p className="mt-1.5 text-data text-text-secondary">
        Email is used for account notifications. Appointment emails may include the current status and scheduled time;
        other emails contain no message previews, laboratory results or clinical details. SMS is not used.
      </p>
      <div className="mt-4 border-t border-border pt-4">
        {resource.state === "loading" ? (
          <LoadingRows label="Loading email notification preferences" rows={1} />
        ) : resource.state === "error" ? (
          <ErrorRow title="Email preferences could not be loaded." onRetry={reload} status={resource.status} />
        ) : (
          <>
            <label className="flex min-h-11 items-center gap-3 text-data font-semibold text-ink">
              <input
                type="checkbox"
                checked={resource.data.emailEnabled}
                disabled={saving}
                onChange={(event) => void update(event.currentTarget.checked)}
                className="size-5 accent-green-800"
              />
              Send me email notifications
            </label>
            {!resource.data.emailVerified ? (
              <p className="mt-2 text-caption text-warning">
                Email notifications will be sent only after your sign-in email is verified.
              </p>
            ) : null}
            {feedback ? (
              <Alert
                tone={feedback.tone}
                title={feedback.message}
                live={feedback.tone === "critical" ? "assertive" : "polite"}
                className="mt-3"
              />
            ) : null}
          </>
        )}
      </div>
    </Card>
  );
}
