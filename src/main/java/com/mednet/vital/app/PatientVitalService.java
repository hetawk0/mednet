package com.mednet.vital.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
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
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.record.data.ClinicalRecordEntity;
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.vital.data.PatientVitalEntity;
import com.mednet.vital.data.PatientVitalRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientVitalService {

    private final PatientCareAccess careAccess;
    private final PatientVitalRepository vitals;
    private final ClinicalRecordRepository records;
    private final AdminAuditEventRepository auditEvents;

    public PatientVitalService(
            PatientCareAccess careAccess,
            PatientVitalRepository vitals,
            ClinicalRecordRepository records,
            AdminAuditEventRepository auditEvents) {
        this.careAccess = careAccess;
        this.vitals = vitals;
        this.records = records;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public PatientVitalDetails create(
            String patientEmail,
            String metric,
            BigDecimal value,
            String unit,
            Instant recordedAt) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        if (recordedAt.isAfter(Instant.now().plusSeconds(300))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A vital reading cannot be from the future");
        }
        String normalizedMetric = metric.trim();
        String normalizedUnit = unit.trim();
        ClinicalRecordEntity record = records.save(new ClinicalRecordEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                patient.getId(),
                null,
                "VITAL",
                normalizedMetric,
                null,
                value.stripTrailingZeros().toPlainString() + " " + normalizedUnit + " (patient-reported)",
                recordedAt,
                null));
        PatientVitalEntity vital = vitals.save(new PatientVitalEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                patient.getId(),
                normalizedMetric,
                value,
                normalizedUnit,
                recordedAt,
                record.getId()));
        audit(patientEmail, "vital.recorded", vital.getId());
        return details(vital);
    }

    @Transactional
    public Page<PatientVitalDetails> patientVitals(String patientEmail, int page, int size) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        audit(patientEmail, "vital.accessed", patient.getId());
        return vitals.findByPatientAccountIdOrderByRecordedAtDescCreatedAtDesc(
                        patient.getId(), pageable(page, size))
                .map(PatientVitalService::details);
    }

    @Transactional
    public Page<PatientVitalDetails> providerVitals(
            String providerEmail, String patientAccountId, int page, int size) {
        PlatformAccountEntity patient = careAccess.requirePatientById(patientAccountId);
        ProviderApplicationEntity provider = careAccess.requireApprovedProviderByEmail(providerEmail);
        careAccess.requireRecordAccess(patient.getId(), provider.getId());
        Page<PatientVitalDetails> result = vitals.findByPatientAccountIdOrderByRecordedAtDescCreatedAtDesc(
                        patient.getId(), pageable(page, size))
                .map(PatientVitalService::details);
        audit(providerEmail, "vital.accessed", patient.getId());
        return result;
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
    }

    private void audit(String actorEmail, String action, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(Locale.ROOT),
                action,
                "patient_vital",
                resourceId));
    }

    private static PatientVitalDetails details(PatientVitalEntity vital) {
        return new PatientVitalDetails(
                vital.getId(),
                vital.getPatientAccountId(),
                vital.getMetric(),
                vital.getValue(),
                vital.getUnit(),
                vital.getRecordedAt(),
                vital.getSource(),
                vital.getClinicalRecordId());
    }

    public record PatientVitalDetails(
            String id,
            String patientAccountId,
            String metric,
            BigDecimal value,
            String unit,
            Instant recordedAt,
            String source,
            String clinicalRecordId) {
    }
}
