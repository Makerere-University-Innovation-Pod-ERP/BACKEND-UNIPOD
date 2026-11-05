package org.unipod.unipoderpbackend.submissions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class NotificationOutbox {
    private final JdbcTemplate jdbcTemplate;

    public NotificationOutbox(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordForwarded(
            UUID submissionId,
            UUID sectorId,
            String sectorName,
            String notificationEmail,
            String submissionTitle,
            String caseCode,
            String actor,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    id, aggregate_id, event_type, payload, occurred_at, next_attempt_at
                )
                VALUES (
                    ?, ?, 'submission.forwarded',
                    jsonb_build_object(
                        'submissionId', ?::text,
                        'sectorId', ?::text,
                        'sectorName', ?::text,
                        'notificationEmail', ?::text,
                        'submissionTitle', ?::text,
                        'caseCode', ?::text,
                        'forwardedBy', ?::text
                    ),
                    ?, ?
                )
                """,
                UUID.randomUUID(),
                submissionId,
                submissionId.toString(),
                sectorId.toString(),
                sectorName,
                notificationEmail,
                submissionTitle,
                caseCode,
                actor,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt)
        );
    }
}
