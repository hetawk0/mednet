package com.mednet.medication.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MedicationReminderScheduler {

    private final MedicationService medications;

    public MedicationReminderScheduler(MedicationService medications) {
        this.medications = medications;
    }

    @Scheduled(fixedDelayString = "${mednet.medication.reminder-scan-ms:30000}")
    public void dispatchDueReminders() {
        medications.createDueReminders();
    }
}
