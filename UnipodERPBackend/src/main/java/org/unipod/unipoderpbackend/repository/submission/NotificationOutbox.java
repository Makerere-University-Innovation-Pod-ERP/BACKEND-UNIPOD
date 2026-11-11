package org.unipod.unipoderpbackend.repository.submission;

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

    public void recordForwarded(ForwardedNotification notification) {
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
                notification.submissionId(),
                notification.submissionId().toString(),
                notification.sectorId().toString(),
                notification.sectorName(),
                notification.notificationEmail(),
                notification.submissionTitle(),
                notification.caseCode(),
                notification.actor(),
                Timestamp.from(notification.occurredAt()),
                Timestamp.from(notification.occurredAt())
        );
    }

    public void recordInformationRequested(
            UUID submissionId,
            String caseCode,
            String submitterId,
            String submitterEmail,
            String note,
            String actor,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    id, aggregate_id, event_type, payload, occurred_at, next_attempt_at
                )
                VALUES (
                    ?, ?, 'submission.information_requested',
                    jsonb_build_object(
                        'submissionId', ?::text,
                        'caseCode', ?::text,
                        'submitterId', ?::text,
                        'notificationEmail', ?::text,
                        'note', ?::text,
                        'requestedBy', ?::text
                    ),
                    ?, ?
                )
                """,
                UUID.randomUUID(),
                submissionId,
                submissionId.toString(),
                caseCode,
                submitterId,
                submitterEmail,
                note,
                actor,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt)
        );
    }

    public void recordReminderSent(
            UUID submissionId,
            String caseCode,
            String submitterEmail,
            String note,
            String actor,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    id, aggregate_id, event_type, payload, occurred_at, next_attempt_at
                )
                VALUES (
                    ?, ?, 'submission.reminder_sent',
                    jsonb_build_object(
                        'submissionId', ?::text,
                        'caseCode', ?::text,
                        'notificationEmail', ?::text,
                        'note', ?::text,
                        'requestedBy', ?::text
                    ),
                    ?, ?
                )
                """,
                UUID.randomUUID(),
                submissionId,
                submissionId.toString(),
                caseCode,
                submitterEmail,
                note,
                actor,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt)
        );
    }

    public record ForwardedNotification(
            UUID submissionId,
            UUID sectorId,
            String sectorName,
            String notificationEmail,
            String submissionTitle,
            String caseCode,
            String actor,
            Instant occurredAt
    ) {
    }
}
