package org.unipod.unipoderpbackend.service.submission;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxNotificationDispatcherTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final UUID eventId = UUID.randomUUID();
    private OutboxNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxNotificationDispatcher(
                jdbcTemplate,
                mailSender,
                new ObjectMapper(),
                mock(PlatformTransactionManager.class),
                "no-reply@example.invalid",
                "smtp.example.invalid"
        );
    }

    @Test
    void sendsForwardedCaseAndMarksOutboxEventPublished() {
        dispatcher.dispatch(new OutboxNotificationDispatcher.OutboxMessage(
                eventId,
                """
                        {
                          "notificationEmail": "sector@example.invalid",
                          "sectorName": "Agriculture",
                          "caseCode": "GEN-2026-000001",
                          "submissionTitle": "Irrigation monitor"
                        }
                        """,
                        1,
                        UUID.randomUUID()
        ));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals("sector@example.invalid", message.getValue().getTo()[0]);
        assertEquals("Submission GEN-2026-000001 forwarded to Agriculture", message.getValue().getSubject());
        verify(jdbcTemplate).update(
                contains("published_at = CURRENT_TIMESTAMP"),
                eq((Object) eventId),
                any(Object.class)
        );
    }

    @Test
    void retriesMailFailureWithoutExposingProviderDetails() {
        doThrow(new MailSendException("provider secret details"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        dispatcher.dispatch(new OutboxNotificationDispatcher.OutboxMessage(
                eventId,
                """
                        {
                          "notificationEmail": "sector@example.invalid",
                          "sectorName": "Agriculture",
                          "caseCode": "GEN-2026-000001",
                          "submissionTitle": "Irrigation monitor"
                        }
                        """,
                        1,
                        UUID.randomUUID()
        ));

        var retryArguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("next_attempt_at"), retryArguments.capture());
        assertEquals("MailSendException", retryArguments.getValue()[3]);
        org.junit.jupiter.api.Assertions.assertFalse(
                java.util.Arrays.toString(retryArguments.getValue()).contains("provider secret details")
        );
        verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(contains("published_at = CURRENT_TIMESTAMP"), any(Object[].class));
    }
}
