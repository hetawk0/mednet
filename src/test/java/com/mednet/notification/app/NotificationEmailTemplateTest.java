package com.mednet.notification.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.mednet.notification.data.NotificationEntity;

class NotificationEmailTemplateTest {

    @Test
    void appointmentNoticeContainsOnlyAllowedTimeAndStatusDetails() {
        NotificationEntity notification = notification("APPOINTMENT", "Appointment <updated>", "appointment");

        var content = NotificationEmailTemplate.render(
                notification,
                "https://mednet.example.test/",
                new NotificationEmailTemplate.AppointmentDetails(
                        "CONFIRMED", Instant.parse("2026-10-26T09:30:00Z")));

        assertThat(content.subject()).isEqualTo("MedNet appointment update");
        assertThat(content.html())
                .contains("CONFIRMED · Mon, 26 Oct 2026 at 09:30 UTC")
                .contains("https://mednet.example.test")
                .doesNotContain("<updated>");
        assertThat(content.text()).contains("CONFIRMED · Mon, 26 Oct 2026 at 09:30 UTC");
    }

    @Test
    void messageAndLaboratoryNotificationsNeverIncludeResourceContent() {
        for (String eventType : new String[] { "MESSAGE", "LAB_RESULT" }) {
            var content = NotificationEmailTemplate.render(
                    notification(
                            eventType,
                            "secret clinical data: result text and message body",
                            eventType.equals("MESSAGE") ? "conversation" : "laboratory_result"),
                    "https://mednet.example.test",
                    null);

            assertThat(content.subject()).isEqualTo("MedNet account update");
            assertThat(content.html()).doesNotContain("Appointment:");
            assertThat(content.html()).contains("A new update is available in your MedNet account.");
            assertThat(content.text()).doesNotContain("result text").doesNotContain("message body");
            assertThat(content.html()).doesNotContain("secret clinical data");
        }
    }

    private static NotificationEntity notification(String eventType, String title, String resourceType) {
        return new NotificationEntity("notification-id", "account-id", "event-key", eventType, title, resourceType, "resource-id");
    }
}
