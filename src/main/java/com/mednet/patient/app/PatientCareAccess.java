package com.mednet.patient.app;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.record.data.PatientProviderRecordConsentRepository;

@Component
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientCareAccess {

    private static final List<String> CARE_STATUSES = List.of("CONFIRMED", "COMPLETED", "RESCHEDULE_REQUESTED");

    private final PlatformAccountRepository accounts;
    private final ProviderApplicationRepository providers;
    private final AppointmentRepository appointments;
    private final PatientProviderRecordConsentRepository consents;

    public PatientCareAccess(
            PlatformAccountRepository accounts,
            ProviderApplicationRepository providers,
            AppointmentRepository appointments,
            PatientProviderRecordConsentRepository consents) {
        this.accounts = accounts;
        this.providers = providers;
        this.appointments = appointments;
        this.consents = consents;
    }

    public PlatformAccountEntity requirePatient(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(account -> "PATIENT".equals(account.getAccountType())
                        && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient access is not allowed"));
    }

    public PlatformAccountEntity requirePatientById(String patientAccountId) {
        return accounts.findById(patientAccountId)
                .filter(account -> "PATIENT".equals(account.getAccountType())
                        && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    public PlatformAccountEntity requireActiveProviderAccount(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(account -> ("PROVIDER".equals(account.getAccountType())
                        || "PATIENT".equals(account.getAccountType()))
                        && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    }

    public ProviderApplicationEntity requireApprovedProvider(String providerId) {
        ProviderApplicationEntity provider = providers.findById(providerId)
                .filter(application -> "APPROVED".equals(application.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        requireActiveProviderAccount(provider.getEmail());
        return provider;
    }

    public ProviderApplicationEntity requireApprovedProviderByEmail(String email) {
        ProviderApplicationEntity provider = providers
                .findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, "APPROVED")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        requireActiveProviderAccount(email);
        return provider;
    }

    public void requireCareRelationship(String patientAccountId, String providerId) {
        appointments.findFirstByPatientAccountIdAndProviderApplicationIdAndStatusInOrderByCreatedAtDesc(
                        patientAccountId, providerId, CARE_STATUSES)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "A confirmed care relationship is required"));
    }

    public void requireRecordConsent(String patientAccountId, String providerId) {
        consents.findForUpdate(patientAccountId, providerId)
                .filter(consent -> "GRANTED".equals(consent.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active patient consent is required"));
    }

    public void requireRecordAccess(String patientAccountId, String providerId) {
        requireCareRelationship(patientAccountId, providerId);
        requireRecordConsent(patientAccountId, providerId);
    }
}
