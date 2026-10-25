package com.mednet.admin.reporting;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminReportRepository {

    private final JdbcTemplate jdbc;

    public AdminReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminReportService.AggregatePoint> accounts(OffsetDateTime from, OffsetDateTime to) {
        return query("""
                select extract(year from created_at) as report_year,
                       extract(month from created_at) as report_month,
                       account_type as category,
                       status,
                       count(*) as total
                from platform_accounts
                where created_at >= ? and created_at < ?
                group by extract(year from created_at), extract(month from created_at), account_type, status
                order by report_year, report_month, category, status
                """, from, to);
    }

    public List<AdminReportService.AggregatePoint> appointments(OffsetDateTime from, OffsetDateTime to) {
        return query("""
                select extract(year from appointment.created_at) as report_year,
                       extract(month from appointment.created_at) as report_month,
                       slot.consultation_mode as category,
                       appointment.status,
                       count(*) as total
                from appointments appointment
                join provider_availability_slots slot on slot.id = appointment.availability_slot_id
                where appointment.created_at >= ? and appointment.created_at < ?
                group by extract(year from appointment.created_at),
                         extract(month from appointment.created_at), slot.consultation_mode, appointment.status
                order by report_year, report_month, category, appointment.status
                """, from, to);
    }

    public List<AdminReportService.AggregatePoint> serviceRequests(OffsetDateTime from, OffsetDateTime to) {
        return query("""
                select extract(year from created_at) as report_year,
                       extract(month from created_at) as report_month,
                       request_type as category,
                       status,
                       count(*) as total
                from patient_service_requests
                where created_at >= ? and created_at < ?
                group by extract(year from created_at), extract(month from created_at), request_type, status
                order by report_year, report_month, category, status
                """, from, to);
    }

    public List<AdminReportService.AggregatePoint> laboratoryResults(OffsetDateTime from, OffsetDateTime to) {
        return query("""
                select extract(year from created_at) as report_year,
                       extract(month from created_at) as report_month,
                       'LABORATORY' as category,
                       status,
                       count(*) as total
                from laboratory_results
                where created_at >= ? and created_at < ?
                group by extract(year from created_at), extract(month from created_at), status
                order by report_year, report_month, status
                """, from, to);
    }

    private List<AdminReportService.AggregatePoint> query(String sql, OffsetDateTime from, OffsetDateTime to) {
        return jdbc.query(
                sql,
                statement -> {
                    statement.setObject(1, from.withOffsetSameInstant(ZoneOffset.UTC));
                    statement.setObject(2, to.withOffsetSameInstant(ZoneOffset.UTC));
                },
                AdminReportRepository::point);
    }

    private static AdminReportService.AggregatePoint point(ResultSet result, int row) throws SQLException {
        int year = result.getInt("report_year");
        int month = result.getInt("report_month");
        return new AdminReportService.AggregatePoint(
                "%04d-%02d".formatted(year, month),
                result.getString("category"),
                result.getString("status"),
                result.getLong("total"));
    }
}
