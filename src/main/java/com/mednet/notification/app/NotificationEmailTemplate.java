package com.mednet.notification.app;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.mednet.notification.data.NotificationEntity;

public final class NotificationEmailTemplate {

    private static final DateTimeFormatter APPOINTMENT_TIME =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private NotificationEmailTemplate() {
    }

    public static Content render(NotificationEntity notification, String baseUrl, AppointmentDetails appointment) {
        String safeBaseUrl = escapeHtml(baseUrl);
        boolean appointmentUpdate = "APPOINTMENT".equals(notification.getEventType()) && appointment != null;
        String subject = appointmentUpdate ? "MedNet appointment update" : "MedNet account update";
        String message = appointmentUpdate
                ? "Your appointment was updated."
                : "A new update is available in your MedNet account.";
        String details = appointmentUpdate
                ? appointment.status() + " · " + APPOINTMENT_TIME.format(appointment.startsAt())
                : "";
        String safeDetails = escapeHtml(details);
        String html = """
                <!doctype html>
                <html lang="en"><body style="font-family:Arial,Helvetica,sans-serif;color:#183329">
                  <main>
                    <h1>MedNet notification</h1>
                    <p>%s</p>
                    %s
                    <p><a href="%s">Sign in to MedNet</a> to view your account.</p>
                    <p>If you no longer want email notifications, update notification settings in your account.</p>
                  </main>
                </body></html>
                """.formatted(
                escapeHtml(message),
                appointmentUpdate ? "<p>Appointment: " + safeDetails + "</p>" : "",
                safeBaseUrl);
        String text = "MedNet notification\n\n" + message
                + (appointmentUpdate ? "\nAppointment: " + details : "")
                + "\n\nSign in to MedNet: " + baseUrl
                + "\nYou can turn off email notifications in your account settings.";
        return new Content(subject, html, text);
    }

    public record AppointmentDetails(String status, Instant startsAt) {
    }

    public record Content(String subject, String html, String text) {
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
