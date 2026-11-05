package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

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
                1
        ));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertEquals("sector@example.invalid", message.getValue().getTo()[0]);
        assertEquals("Submission GEN-2026-000001 forwarded to Agriculture", message.getValue().getSubject());
        verify(jdbcTemplate).update(
                contains("published_at = CURRENT_TIMESTAMP"),
                eq((Object) eventId)
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
                1
        ));

        verify(jdbcTemplate).update(contains("next_attempt_at"), any(Object[].class));
        verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(contains("published_at = CURRENT_TIMESTAMP"), any(Object[].class));
    }
}
