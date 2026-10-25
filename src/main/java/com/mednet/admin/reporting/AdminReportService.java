package com.mednet.admin.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminReportService {

    private static final int MAX_RANGE_DAYS = 731;
    private final AdminReportRepository reports;

    public AdminReportService(AdminReportRepository reports) {
        this.reports = reports;
    }

    public AdminAggregateReport report(LocalDate from, LocalDate through) {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        if (from == null && through == null) {
            through = currentMonth.plusMonths(1).minusDays(1);
            from = currentMonth.minusMonths(11);
        } else if (from == null || through == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide both from and through dates");
        }
        if (from.isAfter(through)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The from date must be on or before through");
        }
        if (through.equals(LocalDate.MAX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The through date is outside the supported range");
        }
        if (ChronoUnit.DAYS.between(from, through) + 1 > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report ranges may not exceed 731 days");
        }
        var start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        var endExclusive = through.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return new AdminAggregateReport(
                from,
                through,
                Instant.now(),
                reports.accounts(start, endExclusive),
                reports.appointments(start, endExclusive),
                reports.serviceRequests(start, endExclusive),
                reports.laboratoryResults(start, endExclusive));
    }

    public record AdminAggregateReport(
            LocalDate from,
            LocalDate through,
            Instant generatedAt,
            List<AggregatePoint> accounts,
            List<AggregatePoint> appointments,
            List<AggregatePoint> serviceRequests,
            List<AggregatePoint> laboratoryResults) {
    }

    public record AggregatePoint(String month, String category, String status, long count) {
    }
}
