package org.unipod.unipoderpbackend.submissions;

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

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

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
                WHERE event_type = 'submission.forwarded'
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
            RETURNING event.id, event.payload::text AS payload, event.attempt_count, event.locked_by
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
            String sectorName = required(payload, "sectorName");
            String caseCode = required(payload, "caseCode");
            String title = required(payload, "submissionTitle");

            message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipient);
            message.setSubject("Submission " + caseCode + " forwarded to " + safeHeader(sectorName));
            message.setText("""
                    A submission has been forwarded to your sector.

                    Case code: %s
                    Title: %s
                    """.formatted(caseCode, title));
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
                result.getString("payload"),
                result.getInt("attempt_count"),
                result.getObject("locked_by", UUID.class)
        );
    }

    private static String required(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
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

    record OutboxMessage(UUID id, String payload, int attemptCount, UUID claimToken) {
    }
}
