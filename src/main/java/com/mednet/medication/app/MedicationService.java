package com.mednet.medication.app;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.medication.data.MedicationDoseLogEntity;
import com.mednet.medication.data.MedicationDoseLogRepository;
import com.mednet.medication.data.MedicationScheduleEntity;
import com.mednet.medication.data.MedicationScheduleRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.record.data.ClinicalRecordEntity;
import com.mednet.record.data.ClinicalRecordRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MedicationService {

    private final PatientCareAccess careAccess;
    private final MedicationScheduleRepository schedules;
    private final MedicationDoseLogRepository doseLogs;
    private final ClinicalRecordRepository records;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;
    private final Clock clock;

    public MedicationService(
            PatientCareAccess careAccess,
            MedicationScheduleRepository schedules,
            MedicationDoseLogRepository doseLogs,
            ClinicalRecordRepository records,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.careAccess = careAccess;
        this.schedules = schedules;
        this.doseLogs = doseLogs;
        this.records = records;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public List<MedicationDetails> list(String email) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        audit(email, "medication.schedules_accessed", patient.getId());
        return schedules.findByPatientAccountIdOrderByCreatedAtDesc(patient.getId()).stream()
                .map(MedicationService::details)
                .toList();
    }

    @Transactional
    public MedicationDetails create(
            String email,
            String name,
            String dose,
            LocalTime reminderTime,
            String timeZone,
            LocalDate startDate,
            LocalDate endDate) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        ZoneId zone = zone(timeZone);
        validateDates(startDate, endDate);
        Instant nextReminder = nextReminder(reminderTime, zone, startDate, endDate);
        MedicationScheduleEntity saved = schedules.save(new MedicationScheduleEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                name.trim(),
                dose.trim(),
                reminderTime,
                zone.getId(),
                startDate,
                endDate,
                nextReminder));
        appendRecord(patient, saved, "medication.schedule_created");
        audit(email, "medication.schedule_created", saved.getId());
        return details(saved);
    }

    @Transactional
    public MedicationDetails update(
            String email,
            String scheduleId,
            String name,
            String dose,
            LocalTime reminderTime,
            String timeZone,
            LocalDate startDate,
            LocalDate endDate) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        MedicationScheduleEntity schedule = ownedScheduleForUpdate(patient.getId(), scheduleId);
        ZoneId zone = zone(timeZone);
        validateDates(startDate, endDate);
        Instant nextReminder = nextReminder(reminderTime, zone, startDate, endDate);
        schedule.update(name.trim(), dose.trim(), reminderTime, zone.getId(), startDate, endDate, nextReminder);
        appendRecord(patient, schedule, "medication.schedule_updated");
        audit(email, "medication.schedule_updated", scheduleId);
        return details(schedule);
    }

    @Transactional
    public MedicationDetails stop(String email, String scheduleId) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        MedicationScheduleEntity schedule = ownedScheduleForUpdate(patient.getId(), scheduleId);
        if ("ACTIVE".equals(schedule.getStatus())) {
            schedule.stop();
            appendRecord(patient, schedule, "medication.schedule_stopped");
            audit(email, "medication.schedule_stopped", scheduleId);
        }
        return details(schedule);
    }

    @Transactional
    public DoseLogDetails logDose(String email, String scheduleId) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        MedicationScheduleEntity schedule = ownedScheduleForUpdate(patient.getId(), scheduleId);
        if (!"ACTIVE".equals(schedule.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stopped medication schedules cannot log a dose");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(schedule.getTimeZone())));
        if (today.isBefore(schedule.getStartDate())
                || (schedule.getEndDate() != null && today.isAfter(schedule.getEndDate()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No dose is scheduled for today");
        }
        MedicationDoseLogEntity log = doseLogs.findByScheduleIdAndDoseDate(scheduleId, today)
                .orElseGet(() -> doseLogs.save(new MedicationDoseLogEntity(
                        UUID.randomUUID().toString(), scheduleId, today, Instant.now(clock))));
        audit(email, "medication.dose_logged", log.getId());
        return toDoseLog(log);
    }

    @Transactional
    public List<DoseLogDetails> doseLogs(String email, String scheduleId) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        ownedSchedule(patient.getId(), scheduleId);
        audit(email, "medication.dose_history_accessed", scheduleId);
        return doseLogs.findByScheduleIdOrderByDoseDateDesc(scheduleId).stream()
                .map(MedicationService::toDoseLog)
                .toList();
    }

    @Transactional
    public void createDueReminders() {
        Instant now = Instant.now(clock);
        List<MedicationScheduleEntity> due = schedules.findByStatusAndNextReminderAtLessThanEqual("ACTIVE", now);
        for (MedicationScheduleEntity candidate : due) {
            MedicationScheduleEntity schedule = schedules.findByIdForUpdate(candidate.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication schedule not found"));
            if (!"ACTIVE".equals(schedule.getStatus()) || schedule.getNextReminderAt().isAfter(now)) {
                continue;
            }
            LocalDate dueDate = schedule.getNextReminderAt().atZone(ZoneId.of(schedule.getTimeZone())).toLocalDate();
            if (schedule.getEndDate() != null && dueDate.isAfter(schedule.getEndDate())) {
                schedule.stop();
                continue;
            }
            notifications.create(
                    schedule.getPatientAccountId(),
                    "medication:" + schedule.getId() + ":" + dueDate,
                    "MEDICATION_REMINDER",
                    "A medication reminder is due",
                    "medication",
                    schedule.getId());
            schedule.advanceReminder(now);
        }
    }

    private MedicationScheduleEntity ownedSchedule(String patientId, String scheduleId) {
        return schedules.findByIdAndPatientAccountId(scheduleId, patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication schedule not found"));
    }

    private MedicationScheduleEntity ownedScheduleForUpdate(String patientId, String scheduleId) {
        MedicationScheduleEntity schedule = schedules.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication schedule not found"));
        if (!patientId.equals(schedule.getPatientAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication schedule not found");
        }
        return schedule;
    }

    private void appendRecord(PlatformAccountEntity patient, MedicationScheduleEntity schedule, String action) {
        String summary = schedule.getMedicationName() + ", " + schedule.getDose()
                + "; daily at " + schedule.getReminderTime() + " " + schedule.getTimeZone()
                + "; patient-entered; status " + schedule.getStatus();
        ClinicalRecordEntity record = records.save(new ClinicalRecordEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                patient.getId(),
                null,
                "MEDICATION",
                schedule.getMedicationName(),
                null,
                summary,
                Instant.now(clock),
                null));
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(), patient.getEmail(), action, "patient_record", record.getId()));
    }

    private void audit(String actorEmail, String action, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(), actorEmail, action, "medication_schedule", resourceId));
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be on or after start date");
        }
    }

    private Instant nextReminder(
            LocalTime time, ZoneId zone, LocalDate startDate, LocalDate endDate) {
        LocalDate date = startDate.isAfter(LocalDate.now(clock.withZone(zone)))
                ? startDate
                : LocalDate.now(clock.withZone(zone));
        Instant candidate = date.atTime(time).atZone(zone).toInstant();
        if (!candidate.isAfter(Instant.now(clock))) {
            date = date.plusDays(1);
            candidate = date.atTime(time).atZone(zone).toInstant();
        }
        if (endDate != null && date.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reminder falls within the schedule dates");
        }
        return candidate;
    }

    private static ZoneId zone(String value) {
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid IANA time zone is required");
        }
    }

    private static MedicationDetails details(MedicationScheduleEntity schedule) {
        return new MedicationDetails(
                schedule.getId(),
                schedule.getMedicationName(),
                schedule.getDose(),
                schedule.getReminderTime(),
                schedule.getTimeZone(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getNextReminderAt(),
                schedule.getStatus(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }

    private static DoseLogDetails toDoseLog(MedicationDoseLogEntity log) {
        return new DoseLogDetails(log.getId(), log.getDoseDate(), log.getTakenAt());
    }

    public record MedicationDetails(
            String id,
            String medicationName,
            String dose,
            LocalTime reminderTime,
            String timeZone,
            LocalDate startDate,
            LocalDate endDate,
            Instant nextReminderAt,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DoseLogDetails(String id, LocalDate doseDate, Instant takenAt) {
    }
}
