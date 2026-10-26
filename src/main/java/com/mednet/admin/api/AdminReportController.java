package com.mednet.admin.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.admin.reporting.AdminReportService;
import com.mednet.admin.reporting.AdminReportService.AdminAggregateReport;
import com.mednet.admin.reporting.AdminReportService.AggregatePoint;
import com.mednet.admin.reporting.AdminReportService.SnapshotPoint;

@RestController
@RequestMapping("/api/v1/admin/reports")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminReportController {

    private final AdminReportService service;

    public AdminReportController(AdminReportService service) {
        this.service = service;
    }

    @GetMapping
    public AdminAggregateReport report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        return service.report(from, through);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        AdminAggregateReport report = service.report(from, through);
        String filename = "mednet-aggregate-report-" + report.from() + "-to-" + report.through() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(csv(report).getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(AdminAggregateReport report) {
        StringBuilder output = new StringBuilder("Period,Dataset,Category,Status,Count\r\n");
        append(output, "accounts", report.accounts());
        output.append("CURRENT,patients,ALL,REGISTERED,")
                .append(report.totalRegisteredPatients())
                .append("\r\n");
        append(output, "patient_registrations", report.patientRegistrations());
        appendSnapshot(output, "provider_status", report.providersByStatus());
        append(output, "appointments", report.appointments());
        append(output, "completed_text_consultations", report.completedVirtualConsultations());
        append(output, "service_requests", report.serviceRequests());
        append(output, "laboratory_results", report.laboratoryResults());
        appendSnapshot(output, "awaiting_action", report.awaitingAction());
        return output.toString();
    }

    private static void append(StringBuilder output, String dataset, List<AggregatePoint> points) {
        for (AggregatePoint point : points) {
            output.append(cell(point.month())).append(',')
                    .append(cell(dataset)).append(',')
                    .append(cell(point.category())).append(',')
                    .append(cell(point.status())).append(',')
                    .append(point.count()).append("\r\n");
        }
    }

    private static void appendSnapshot(
            StringBuilder output, String dataset, List<SnapshotPoint> points) {
        for (SnapshotPoint point : points) {
            output.append("CURRENT,")
                    .append(cell(dataset)).append(',')
                    .append(cell(point.category())).append(',')
                    .append(cell(point.status())).append(',')
                    .append(point.count()).append("\r\n");
        }
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        if (safe.matches("^[\\s\\p{Cc}]*[=+@-].*")) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
