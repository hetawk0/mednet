package com.mednet.notification.app;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.notification.data.NotificationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationEmailContentFactory {

    private final AppointmentRepository appointments;
    private final ProviderAvailabilitySlotRepository slots;
    private final ProviderApplicationRepository providers;
    private final PlatformAccountRepository accounts;
    private final String webBaseUrl;

    public NotificationEmailContentFactory(
            AppointmentRepository appointments,
            ProviderAvailabilitySlotRepository slots,
            ProviderApplicationRepository providers,
            PlatformAccountRepository accounts,
            @Value("${mednet.notifications.email.web-base-url:http://localhost:3000}") String webBaseUrl) {
        this.appointments = appointments;
        this.slots = slots;
        this.providers = providers;
        this.accounts = accounts;
        this.webBaseUrl = webBaseUrl == null ? "" : webBaseUrl.trim().replaceAll("/+$", "");
    }

    public NotificationEmailTemplate.Content create(
            NotificationEntity notification, String recipientAccountId) {
        NotificationEmailTemplate.AppointmentDetails appointment =
                "APPOINTMENT".equals(notification.getEventType())
                                && "appointment".equals(notification.getResourceType())
                        ? appointmentDetails(notification.getResourceId(), recipientAccountId).orElse(null)
                        : null;
        return NotificationEmailTemplate.render(notification, webBaseUrl, appointment);
    }

    private Optional<NotificationEmailTemplate.AppointmentDetails> appointmentDetails(
            String appointmentId, String recipientAccountId) {
        return appointments.findById(appointmentId)
                .filter(appointment -> {
                    if (appointment.getPatientAccountId().equals(recipientAccountId)) {
                        return true;
                    }
                    return providers.findById(appointment.getProviderApplicationId())
                            .flatMap(provider -> accounts.findFirstByEmailIgnoreCase(provider.getEmail()))
                            .map(account -> account.getId().equals(recipientAccountId))
                            .orElse(false);
                })
                .flatMap(appointment -> slots.findById(appointment.getAvailabilitySlotId())
                        .map(slot -> new NotificationEmailTemplate.AppointmentDetails(
                                appointment.getStatus(), slot.getStartsAt())));
    }
}
