package com.mednet.appointment.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AppointmentReminderScheduler {

    private final AppointmentReminderService reminders;

    public AppointmentReminderScheduler(AppointmentReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${mednet.appointment.reminder-scan-ms:30000}")
    public void dispatchDueReminders() {
        reminders.createDueReminders();
    }
}
