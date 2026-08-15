package com.example.inventory.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
class AuthenticationRateLimitRepository {

    private final JdbcClient jdbc;

    AuthenticationRateLimitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Attempt> increment(String key, Duration window,
                                int maximumTrackedKeys) {
        return jdbc.sql("""
                        INSERT INTO authentication_rate_limit_buckets (
                            bucket_key, window_started_at, expires_at,
                            attempt_count
                        )
                        SELECT :key, CURRENT_TIMESTAMP,
                               CURRENT_TIMESTAMP + make_interval(
                                   secs => CAST(:windowMillis AS double precision) / 1000.0),
                               1
                        WHERE EXISTS (
                            SELECT 1 FROM authentication_rate_limit_buckets
                            WHERE bucket_key = :key
                        ) OR (
                            SELECT count(*)
                            FROM authentication_rate_limit_buckets
                            WHERE expires_at > CURRENT_TIMESTAMP
                        ) < :maximumTrackedKeys
                        ON CONFLICT (bucket_key) DO UPDATE SET
                            window_started_at = CASE
                                WHEN authentication_rate_limit_buckets.expires_at
                                     <= CURRENT_TIMESTAMP
                                THEN CURRENT_TIMESTAMP
                                ELSE authentication_rate_limit_buckets.window_started_at
                            END,
                            expires_at = CASE
                                WHEN authentication_rate_limit_buckets.expires_at
                                     <= CURRENT_TIMESTAMP
                                THEN CURRENT_TIMESTAMP + make_interval(
                                    secs => CAST(:windowMillis AS double precision) / 1000.0)
                                ELSE authentication_rate_limit_buckets.expires_at
                            END,
                            attempt_count = CASE
                                WHEN authentication_rate_limit_buckets.expires_at
                                     <= CURRENT_TIMESTAMP
                                THEN 1
                                ELSE authentication_rate_limit_buckets.attempt_count + 1
                            END
                        RETURNING attempt_count,
                            GREATEST(1, CEIL(EXTRACT(EPOCH FROM (
                                expires_at - CURRENT_TIMESTAMP))))::bigint
                                AS retry_after_seconds
                        """)
                .param("key", key)
                .param("windowMillis", window.toMillis())
                .param("maximumTrackedKeys", maximumTrackedKeys)
                .query((result, rowNumber) -> new Attempt(
                        result.getLong("attempt_count"),
                        result.getLong("retry_after_seconds")))
                .optional();
    }

    void delete(String key) {
        jdbc.sql("""
                        DELETE FROM authentication_rate_limit_buckets
                        WHERE bucket_key = :key
                        """)
                .param("key", key)
                .update();
    }

    void deleteExpired(int maximumRows) {
        jdbc.sql("""
                        DELETE FROM authentication_rate_limit_buckets
                        WHERE bucket_key IN (
                            SELECT bucket_key
                            FROM authentication_rate_limit_buckets
                            WHERE expires_at <= CURRENT_TIMESTAMP
                            ORDER BY expires_at
                            LIMIT :maximumRows
                        )
                        """)
                .param("maximumRows", maximumRows)
                .update();
    }

    record Attempt(long count, long retryAfterSeconds) { }
}
