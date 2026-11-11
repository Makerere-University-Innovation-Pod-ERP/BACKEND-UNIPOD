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

    /**
     * Records a submitter-facing lifecycle notification (submitted, reply
     * received, forwarded copy, confirmed as project, rejected).
     */
    public void recordSubmitterNotification(
            UUID submissionId,
            String eventType,
            String caseCode,
            String recipient,
            String subject,
            String message,
            String actor,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    id, aggregate_id, event_type, payload, occurred_at, next_attempt_at
                )
                VALUES (
                    ?, ?, ?,
                    jsonb_build_object(
                        'submissionId', ?::text,
                        'caseCode', ?::text,
                        'notificationEmail', ?::text,
                        'subject', ?::text,
                        'message', ?::text,
                        'triggeredBy', ?::text
                    ),
                    ?, ?
                )
                """,
                UUID.randomUUID(),
                submissionId,
                eventType,
                submissionId.toString(),
                caseCode,
                recipient,
                subject,
                message,
                actor,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt)
        );
    }

    /**
     * Records the one-time password setup link created with an assisted
     * submitter account. The link carries the raw token so the notifier can
     * deliver it; only a hash of the same token is stored on the user record,
     * and the token can be consumed once and expires after 72 hours.
     */
    public void recordPasswordSetup(
            UUID userId,
            String email,
            String setPasswordUrl,
            Instant expiresAt,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification_outbox (
                    id, aggregate_id, event_type, payload, occurred_at, next_attempt_at
                )
                VALUES (
                    ?, ?, 'auth.password_setup',
                    jsonb_build_object(
                        'userId', ?::text,
                        'notificationEmail', ?::text,
                        'setPasswordUrl', ?::text,
                        'expiresAt', ?::text
                    ),
                    ?, ?
                )
                """,
                UUID.randomUUID(),
                userId,
                userId.toString(),
                email,
                setPasswordUrl,
                expiresAt.toString(),
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
