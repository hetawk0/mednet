package com.mednet.appointment.app;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.appointment.data.AppointmentEntity;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotEntity;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AppointmentReminderService {

    private static final Duration FIRST_REMINDER = Duration.ofHours(24);
    private static final Duration FINAL_REMINDER = Duration.ofHours(1);

    private final AppointmentRepository appointments;
    private final ProviderAvailabilitySlotRepository availabilitySlots;
    private final ProviderApplicationRepository providers;
    private final PlatformAccountRepository accounts;
    private final NotificationService notifications;

    public AppointmentReminderService(
            AppointmentRepository appointments,
            ProviderAvailabilitySlotRepository availabilitySlots,
            ProviderApplicationRepository providers,
            PlatformAccountRepository accounts,
            NotificationService notifications) {
        this.appointments = appointments;
        this.availabilitySlots = availabilitySlots;
        this.providers = providers;
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @Transactional
    public void createDueReminders() {
        Instant now = Instant.now();
        List<AppointmentEntity> upcoming = appointments.findConfirmedAppointmentsStartingBetween(
                now, now.plus(FIRST_REMINDER));
        for (AppointmentEntity appointment : upcoming) {
            ProviderAvailabilitySlotEntity slot = availabilitySlots.findById(appointment.getAvailabilitySlotId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment slot not found"));
            Duration timeUntilStart = Duration.between(now, slot.getStartsAt());
            if (timeUntilStart.compareTo(FINAL_REMINDER) <= 0) {
                createReminder(appointment, "1h", "You have an appointment within 1 hour");
            } else {
                createReminder(appointment, "24h", "You have an appointment within 24 hours");
            }
        }
    }

    private void createReminder(AppointmentEntity appointment, String window, String title) {
        ProviderApplicationEntity provider = providers.findById(appointment.getProviderApplicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment provider not found"));
        PlatformAccountEntity providerAccount = accounts.findFirstByEmailIgnoreCase(provider.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider account not found"));

        notify(appointment, appointment.getPatientAccountId(), window, title);
        notify(appointment, providerAccount.getId(), window, title);
    }

    private void notify(AppointmentEntity appointment, String recipientAccountId, String window, String title) {
        notifications.create(
                recipientAccountId,
                "appointment:" + appointment.getId() + ":reminder:" + window + ":" + recipientAccountId,
                "APPOINTMENT",
                title,
                "appointment",
                appointment.getId());
    }
}
