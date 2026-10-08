package com.mednet.record.app;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.record.data.ClinicalRecordEntity;
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.record.data.PatientProviderRecordConsentEntity;
import com.mednet.record.data.PatientProviderRecordConsentRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientRecordService {

    private static final List<String> CARE_RELATIONSHIP_STATUSES =
            List.of("CONFIRMED", "COMPLETED", "RESCHEDULE_REQUESTED");

    private final PlatformAccountRepository accounts;
    private final ProviderApplicationRepository providers;
    private final AppointmentRepository appointments;
    private final PatientProviderRecordConsentRepository consents;
    private final ClinicalRecordRepository records;
    private final AdminAuditEventRepository auditEvents;

    public PatientRecordService(
            PlatformAccountRepository accounts,
            ProviderApplicationRepository providers,
            AppointmentRepository appointments,
            PatientProviderRecordConsentRepository consents,
            ClinicalRecordRepository records,
            AdminAuditEventRepository auditEvents) {
        this.accounts = accounts;
        this.providers = providers;
        this.appointments = appointments;
        this.consents = consents;
        this.records = records;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public List<RecordConsent> consents(String patientEmail) {
        PlatformAccountEntity patient = requirePatient(patientEmail);
        return consents.findByPatientAccountIdOrderByUpdatedAtDesc(patient.getId()).stream()
                .map(consent -> providers.findById(consent.getProviderApplicationId())
                        .map(provider -> new RecordConsent(
                                consent.getProviderApplicationId(),
                                provider.getDisplayName(),
                                provider.getSpecialty(),
                                consent.getStatus(),
                                consent.getGrantedAt(),
                                consent.getRevokedAt()))
                        .orElseThrow(PatientRecordService::notFound))
                .toList();
    }

    @Transactional
    public RecordConsent grantConsent(String patientEmail, String providerId) {
        PlatformAccountEntity patient = requirePatient(patientEmail);
        ProviderApplicationEntity provider = providers.findByIdForUpdate(providerId)
                .filter(application -> "APPROVED".equals(application.getStatus()))
                .orElseThrow(PatientRecordService::notFound);
        requireActiveProviderAccount(provider.getEmail());
        requireCareRelationship(patient.getId(), provider.getId());

        Optional<PatientProviderRecordConsentEntity> existing =
                consents.findForUpdate(patient.getId(), provider.getId());
        PatientProviderRecordConsentEntity consent = existing.orElseGet(() ->
                new PatientProviderRecordConsentEntity(
                        UUID.randomUUID().toString(), patient.getId(), provider.getId()));
        if (existing.isEmpty() || !"GRANTED".equals(consent.getStatus())) {
            consent.grant();
            consents.save(consent);
            audit(patientEmail, "record_consent.granted", "record_consent", consent.getId());
        }
        return toConsent(consent, provider);
    }

    @Transactional
    public RecordConsent revokeConsent(String patientEmail, String providerId) {
        PlatformAccountEntity patient = requirePatient(patientEmail);
        ProviderApplicationEntity provider = providers.findById(providerId)
                .orElseThrow(PatientRecordService::notFound);
        PatientProviderRecordConsentEntity consent = consents.findForUpdate(patient.getId(), provider.getId())
                .orElseThrow(PatientRecordService::notFound);
        if ("GRANTED".equals(consent.getStatus())) {
            consent.revoke();
            consents.save(consent);
            audit(patientEmail, "record_consent.revoked", "record_consent", consent.getId());
        }
        return toConsent(consent, provider);
    }

    @Transactional
    public Page<RecordDetails> patientRecords(String patientEmail, int page, int size) {
        PlatformAccountEntity patient = requirePatient(patientEmail);
        Page<RecordDetails> result = records.findByPatientAccountIdOrderByEffectiveAtDescCreatedAtDesc(
                        patient.getId(), pageRequest(page, size))
                .map(this::toRecordDetails);
        audit(patientEmail, "record.accessed", "patient_record", patient.getId());
        return result;
    }

    @Transactional
    public Page<RecordDetails> providerRecords(
            String providerEmail, String patientAccountId, int page, int size) {
        PlatformAccountEntity patient = accounts.findById(patientAccountId)
                .filter(account -> "PATIENT".equals(account.getAccountType()))
                .orElseThrow(PatientRecordService::notFound);
        ProviderApplicationEntity provider = approvedProviderByEmail(providerEmail);
        requireRecordAccess(patient.getId(), provider.getId());
        Page<RecordDetails> result = records.findByPatientAccountIdOrderByEffectiveAtDescCreatedAtDesc(
                        patient.getId(), pageRequest(page, size))
                .map(this::toRecordDetails);
        audit(providerEmail, "record.accessed", "patient_record", patient.getId());
        return result;
    }

    @Transactional
    public RecordDetails addRecord(
            String providerEmail,
            String patientAccountId,
            String category,
            String title,
            String clinicalCode,
            String summary,
            Instant effectiveAt,
            String amendsRecordId) {
        PlatformAccountEntity patient = accounts.findById(patientAccountId)
                .filter(account -> "PATIENT".equals(account.getAccountType()))
                .orElseThrow(PatientRecordService::notFound);
        ProviderApplicationEntity provider = approvedProviderByEmail(providerEmail);
        requireRecordAccess(patient.getId(), provider.getId());

        String normalizedCategory = category.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ENCOUNTER_SUMMARY", "DIAGNOSIS", "PRESCRIPTION", "ALLERGY", "MEDICAL_HISTORY", "DOCUMENT")
                .contains(normalizedCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported medical record category");
        }
        if (effectiveAt.isAfter(Instant.now().plusSeconds(300))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record effective time cannot be in the future");
        }

        if (amendsRecordId != null) {
            records.findByIdAndPatientAccountId(amendsRecordId, patient.getId())
                    .orElseThrow(PatientRecordService::notFound);
        }
        PlatformAccountEntity author = accounts.findFirstByEmailIgnoreCase(providerEmail)
                .filter(account -> "PROVIDER".equals(account.getAccountType()))
                .orElseThrow(PatientRecordService::notFound);
        ClinicalRecordEntity saved = records.save(new ClinicalRecordEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                author.getId(),
                provider.getId(),
                normalizedCategory,
                title.trim(),
                normalizeOptional(clinicalCode),
                summary.trim(),
                effectiveAt,
                amendsRecordId));
        audit(providerEmail, amendsRecordId == null ? "record.created" : "record.amended",
                "patient_record", saved.getId());
        return toRecordDetails(saved);
    }

    private void requireRecordAccess(String patientId, String providerId) {
        requireCareRelationship(patientId, providerId);
        consents.findForUpdate(patientId, providerId)
                .filter(value -> "GRANTED".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Active patient consent is required to access this medical record"));
    }

    private void requireCareRelationship(String patientId, String providerId) {
        appointments.findFirstByPatientAccountIdAndProviderApplicationIdAndStatusInOrderByCreatedAtDesc(
                        patientId, providerId, CARE_RELATIONSHIP_STATUSES)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "A confirmed care relationship is required"));
    }

    private PlatformAccountEntity requirePatient(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(account -> "PATIENT".equals(account.getAccountType())
                        && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Patient record access is not allowed"));
    }

    private ProviderApplicationEntity approvedProvider(String id) {
        ProviderApplicationEntity provider = providers.findById(id)
                .filter(application -> "APPROVED".equals(application.getStatus()))
                .orElseThrow(PatientRecordService::notFound);
        requireActiveProviderAccount(provider.getEmail());
        return provider;
    }

    private ProviderApplicationEntity approvedProviderByEmail(String email) {
        ProviderApplicationEntity provider = providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                        email, "APPROVED")
                .orElseThrow(PatientRecordService::notFound);
        requireActiveProviderAccount(email);
        return provider;
    }

    private PlatformAccountEntity requireActiveProviderAccount(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(account -> ("PROVIDER".equals(account.getAccountType())
                        || "PATIENT".equals(account.getAccountType()))
                        && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(PatientRecordService::notFound);
    }

    private void audit(String actorEmail, String action, String resourceType, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(Locale.ROOT),
                action,
                resourceType,
                resourceId));
    }

    private RecordDetails toRecordDetails(ClinicalRecordEntity record) {
        String authorName = record.getProviderApplicationId() == null
                ? "Patient"
                : providers.findById(record.getProviderApplicationId())
                        .orElseThrow(PatientRecordService::notFound)
                        .getDisplayName();
        return new RecordDetails(
                record.getId(),
                record.getCategory(),
                record.getTitle(),
                record.getClinicalCode(),
                record.getSummary(),
                record.getEffectiveAt(),
                record.getCreatedAt(),
                record.getAmendsRecordId(),
                authorName);
    }

    private static RecordConsent toConsent(
            PatientProviderRecordConsentEntity consent, ProviderApplicationEntity provider) {
        return new RecordConsent(
                provider.getId(),
                provider.getDisplayName(),
                provider.getSpecialty(),
                consent.getStatus(),
                consent.getGrantedAt(),
                consent.getRevokedAt());
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return PageRequest.of(page, size, Sort.unsorted());
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient, provider, or record not found");
    }

    public record RecordConsent(
            String providerId,
            String providerName,
            String specialty,
            String status,
            Instant grantedAt,
            Instant revokedAt) {
    }

    public record RecordDetails(
            String id,
            String category,
            String title,
            String clinicalCode,
            String summary,
            Instant effectiveAt,
            Instant createdAt,
            String amendsRecordId,
            String authorName) {
    }
}
