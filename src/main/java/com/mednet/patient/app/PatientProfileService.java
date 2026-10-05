package com.mednet.patient.app;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.patient.data.PatientProfileEntity;
import com.mednet.patient.data.PatientProfileRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientProfileService {

    private final PlatformAccountRepository accounts;
    private final PatientProfileRepository profiles;
    private final AdminAuditEventRepository auditEvents;

    public PatientProfileService(
            PlatformAccountRepository accounts,
            PatientProfileRepository profiles,
            AdminAuditEventRepository auditEvents) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public PatientProfile get(String email) {
        PlatformAccountEntity account = findPatientAccount(email);
        PatientProfileEntity profile = profiles.findFirstByAccountId(account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
        return toModel(profile);
    }

    @Transactional
    public PatientProfile put(
            String email,
            String fullName,
            LocalDate dateOfBirth,
            String phoneNumber,
            String address) {
        PlatformAccountEntity account = findPatientAccount(email);
        PatientProfileEntity profile = profiles.findFirstByAccountId(account.getId())
                .orElseGet(() -> new PatientProfileEntity(
                        UUID.randomUUID().toString(),
                        account.getId(),
                        normalize(fullName),
                        dateOfBirth,
                        normalize(phoneNumber),
                        normalize(address)));
        boolean created = profile.getCreatedAt() == null;
        if (!created) {
            profile.update(normalize(fullName), dateOfBirth, normalize(phoneNumber), normalize(address));
        }
        PatientProfileEntity saved = profiles.save(profile);
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                account.getEmail(),
                created ? "patient.profile_created" : "patient.profile_updated",
                "patient_profile",
                saved.getId()));
        return toModel(saved);
    }

    private PlatformAccountEntity findPatientAccount(String email) {
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient account not found"));
        if (!"PATIENT".equals(account.getAccountType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient profile access is not allowed");
        }
        return account;
    }

    private static String normalize(String value) {
        return value.trim();
    }

    private static PatientProfile toModel(PatientProfileEntity entity) {
        return new PatientProfile(
                entity.getFullName(),
                entity.getDateOfBirth(),
                entity.getPhoneNumber(),
                entity.getAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public record PatientProfile(
            String fullName,
            LocalDate dateOfBirth,
            String phoneNumber,
            String address,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }
}
