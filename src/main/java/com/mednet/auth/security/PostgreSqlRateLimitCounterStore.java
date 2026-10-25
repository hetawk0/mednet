package com.mednet.auth.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.mednet.auth.security.AuthRateLimitProperties.Limit;
import com.mednet.auth.security.RateLimitCounterStore.Counter;

public final class PostgreSqlRateLimitCounterStore implements RateLimitCounterStore {

    private static final String UPSERT = """
            insert into auth_rate_limit_windows (
                bucket, scope, counter_key, window_started_at, window_seconds, request_count, expires_at)
            values (?, ?, ?, ?, ?, 1, ?)
            on conflict (bucket, scope, counter_key) do update set
                window_started_at = case
                    when auth_rate_limit_windows.expires_at <= excluded.window_started_at
                      or auth_rate_limit_windows.window_seconds <> excluded.window_seconds
                    then excluded.window_started_at
                    else auth_rate_limit_windows.window_started_at
                end,
                request_count = case
                    when auth_rate_limit_windows.expires_at <= excluded.window_started_at
                      or auth_rate_limit_windows.window_seconds <> excluded.window_seconds
                    then 1
                    else least(auth_rate_limit_windows.request_count + 1, ?)
                end,
                window_seconds = excluded.window_seconds,
                expires_at = case
                    when auth_rate_limit_windows.expires_at <= excluded.window_started_at
                      or auth_rate_limit_windows.window_seconds <> excluded.window_seconds
                    then excluded.expires_at
                    else auth_rate_limit_windows.expires_at
                end
            returning window_started_at, request_count
            """;

    private final JdbcTemplate jdbc;

    public PostgreSqlRateLimitCounterStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Counter increment(String bucket, String scope, String keyHash, Instant now, Limit limit) {
        long windowSeconds = windowSeconds(limit);
        var timestamp = now.atOffset(ZoneOffset.UTC);
        return jdbc.queryForObject(
                UPSERT,
                PostgreSqlRateLimitCounterStore::counter,
                bucket,
                scope,
                keyHash,
                timestamp,
                Math.toIntExact(windowSeconds),
                timestamp.plusSeconds(windowSeconds),
                (long) limit.getRequests() + 1);
    }

    @Override
    public Optional<Counter> find(String bucket, String scope, String keyHash, Instant now, Limit limit) {
        return jdbc.query(
                """
                select window_started_at, request_count
                from auth_rate_limit_windows
                where bucket = ? and scope = ? and counter_key = ?
                  and window_seconds = ? and expires_at > ?
                """,
                statement -> {
                    statement.setString(1, bucket);
                    statement.setString(2, scope);
                    statement.setString(3, keyHash);
                    statement.setInt(4, Math.toIntExact(windowSeconds(limit)));
                    statement.setObject(5, now.atOffset(ZoneOffset.UTC));
                },
                result -> result.next() ? Optional.of(counter(result, 1)) : Optional.empty());
    }

    @Override
    public void reset(String bucket, String scope, String keyHash) {
        jdbc.update(
                "delete from auth_rate_limit_windows where bucket = ? and scope = ? and counter_key = ?",
                bucket,
                scope,
                keyHash);
    }

    @Override
    public void purgeExpired(Instant now) {
        jdbc.update(
                "delete from auth_rate_limit_windows where expires_at <= ?",
                now.atOffset(ZoneOffset.UTC));
    }

    @Override
    public long trackedCounters() {
        Long count = jdbc.queryForObject("select count(*) from auth_rate_limit_windows", Long.class);
        return count == null ? 0 : count;
    }

    private static Counter counter(ResultSet result, int row) throws SQLException {
        return new Counter(result.getTimestamp("window_started_at").toInstant(), result.getLong("request_count"));
    }

    private static long windowSeconds(Limit limit) {
        return Math.max(1L, limit.getWindow().toSeconds());
    }
}
