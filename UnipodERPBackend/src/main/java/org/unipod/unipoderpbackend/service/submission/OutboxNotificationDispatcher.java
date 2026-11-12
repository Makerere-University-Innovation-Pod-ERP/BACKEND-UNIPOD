package org.unipod.unipoderpbackend.service.submission;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class OutboxNotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxNotificationDispatcher.class);
    private static final int MAX_BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 10;

    private static final String CLAIM_NEXT_SQL = """
            WITH candidate AS (
                SELECT id
                FROM notification_outbox
                WHERE event_type IN (
                    'submission.forwarded',
                    'submission.information_requested',
                    'submission.reminder_sent',
                    'submission.submitted',
                    'submission.reply_received',
                    'submission.forwarded_copy',
                    'submission.confirmed_as_project',
                    'submission.rejected',
                    'auth.password_setup',
                    'auth.password_reset'
                )
                    AND published_at IS NULL
                    AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
                    AND dead_lettered_at IS NULL
                    AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at, occurred_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE notification_outbox event
            SET locked_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes',
                attempt_count = attempt_count + 1,
                locked_by = CAST(? AS uuid)
            FROM candidate
            WHERE event.id = candidate.id
            RETURNING event.id, event.event_type, event.payload::text AS payload,
                event.attempt_count, event.locked_by
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final String fromAddress;

    public OutboxNotificationDispatcher(
            JdbcTemplate jdbcTemplate,
            JavaMailSender mailSender,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${notifications.email.from}") String fromAddress,
            @Value("${spring.mail.host}") String mailHost
    ) {
        if (fromAddress.isBlank() || mailHost.isBlank() || !isValidAddress(fromAddress)) {
            throw new IllegalStateException(
                    "NOTIFICATION_EMAIL_FROM and SMTP_HOST must be configured when email delivery is enabled."
            );
        }
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.fromAddress = fromAddress;
    }

    @Scheduled(fixedDelayString = "${notifications.outbox.poll-interval:PT15S}")
    public void dispatchPending() {
        for (int i = 0; i < MAX_BATCH_SIZE; i++) {
            Optional<OutboxMessage> claimed = claimNext();
            if (claimed.isEmpty()) {
                return;
            }
            dispatch(claimed.get());
        }
    }

    Optional<OutboxMessage> claimNext() {
        UUID claimToken = UUID.randomUUID();
        return transaction.execute(status -> jdbcTemplate.query(
                CLAIM_NEXT_SQL,
                (result, row) -> mapMessage(result),
                claimToken
        ).stream().findFirst());
    }

    void dispatch(OutboxMessage event) {
        SimpleMailMessage message;
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            String recipient = required(payload, "notificationEmail");
            if (!isValidAddress(recipient)) {
                throw new IllegalArgumentException("Notification recipient is not a valid email address.");
            }

            message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipient);
            if ("auth.password_setup".equals(event.eventType())) {
                String setPasswordUrl = required(payload, "setPasswordUrl");
                message.setSubject("Set up your UNIPOD account password");
                message.setText("""
                        An account has been created for you on the UNIPOD innovation portal.

                        Choose your password using this one-time link:
                        %s

                        If you did not expect this email, you can ignore it.
                        """.formatted(setPasswordUrl));
            } else if ("auth.password_reset".equals(event.eventType())) {
                String setPasswordUrl = required(payload, "setPasswordUrl");
                message.setSubject("Reset your UNIPOD account password");
                message.setText("""
                        Someone asked to reset the password for your UNIPOD account.

                        Choose a new password using this one-time link. It expires in one hour:
                        %s

                        If you did not ask for this, you can ignore this email. Your password has not changed.
                        """.formatted(setPasswordUrl));
            } else if (isSubmitterLifecycleEvent(event.eventType())) {
                String caseCode = required(payload, "caseCode");
                String subject = required(payload, "subject");
                String body = required(payload, "message");
                message.setSubject(safeHeader(subject));
                message.setText("""
                        %s

                        Case code: %s
                        """.formatted(body, caseCode));
            } else if ("submission.forwarded".equals(event.eventType())) {
                String caseCode = required(payload, "caseCode");
                String sectorName = required(payload, "sectorName");
                String title = required(payload, "submissionTitle");
                message.setSubject("Submission " + caseCode + " forwarded to " + safeHeader(sectorName));
                message.setText("""
                        A submission has been forwarded to your sector.

                        Case code: %s
                        Title: %s
                        """.formatted(caseCode, title));
            } else if ("submission.information_requested".equals(event.eventType())) {
                String caseCode = required(payload, "caseCode");
                String note = required(payload, "note");
                String requestedBy = required(payload, "requestedBy");
                message.setSubject("More information needed for submission " + caseCode);
                message.setText("""
                        Reception needs more information to continue reviewing your submission.

                        Case code: %s
                        Requested by: %s
                        Details: %s
                        """.formatted(caseCode, safeHeader(requestedBy), note));
            } else if ("submission.reminder_sent".equals(event.eventType())) {
                String caseCode = required(payload, "caseCode");
                String note = required(payload, "note");
                String requestedBy = required(payload, "requestedBy");
                message.setSubject("Reminder: information still needed for submission " + caseCode);
                message.setText("""
                        This is a reminder that reception is still waiting for information about your submission.

                        Case code: %s
                        Requested by: %s
                        Outstanding items: %s
                        """.formatted(caseCode, safeHeader(requestedBy), note));
            } else {
                throw new IllegalArgumentException("Unsupported notification event type.");
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            retry(event, exception);
            return;
        }

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            retry(event, exception);
            return;
        }
        markPublished(event);
    }

    private void markPublished(OutboxMessage event) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET published_at = CURRENT_TIMESTAMP,
                    locked_until = NULL,
                    locked_by = NULL,
                    last_error = NULL
                WHERE id = ? AND locked_by = ? AND published_at IS NULL
                """, event.id(), event.claimToken());
    }

    private void retry(OutboxMessage event, Exception exception) {
        long delaySeconds = Math.min(3600, 30L << Math.min(event.attemptCount() - 1, 7));
        String errorType = exception instanceof MailException
                ? exception.getClass().getSimpleName()
                : "NotificationDeliveryException";
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET next_attempt_at = CASE
                        WHEN attempt_count >= ? THEN next_attempt_at
                        ELSE CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                    END,
                    dead_lettered_at = CASE
                        WHEN attempt_count >= ? THEN CURRENT_TIMESTAMP
                        ELSE dead_lettered_at
                    END,
                    locked_until = NULL,
                    locked_by = NULL,
                    last_error = ?
                WHERE id = ? AND locked_by = ? AND published_at IS NULL AND dead_lettered_at IS NULL
                    """, MAX_ATTEMPTS, delaySeconds, MAX_ATTEMPTS, errorType, event.id(), event.claimToken());
        log.warn("Notification delivery failed for outbox event {} on attempt {} ({}){}",
                    event.id(), event.attemptCount(), errorType,
                    event.attemptCount() >= MAX_ATTEMPTS ? "; moved to dead letter state" : "");
    }

    private static OutboxMessage mapMessage(ResultSet result) throws SQLException {
        return new OutboxMessage(
                result.getObject("id", UUID.class),
                result.getString("event_type"),
                result.getString("payload"),
                result.getInt("attempt_count"),
                result.getObject("locked_by", UUID.class)
        );
    }

    private static boolean isSubmitterLifecycleEvent(String eventType) {
        return switch (eventType) {
            case "submission.submitted",
                 "submission.reply_received",
                 "submission.forwarded_copy",
                 "submission.confirmed_as_project",
                 "submission.rejected" -> true;
            default -> false;
        };
    }

    private static String required(JsonNode payload, String field) {        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Required notification field is missing.");
        }
        return value.asText();
    }

    private static String safeHeader(String value) {
        return value.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    private static boolean isValidAddress(String value) {
        try {
            InternetAddress address = new InternetAddress(value);
            address.validate();
            return value.equals(address.getAddress());
        } catch (AddressException exception) {
            return false;
        }
    }

    record OutboxMessage(UUID id, String eventType, String payload, int attemptCount, UUID claimToken) {
        OutboxMessage(UUID id, String payload, int attemptCount, UUID claimToken) {
            this(id, "submission.forwarded", payload, attemptCount, claimToken);
        }
    }
}
