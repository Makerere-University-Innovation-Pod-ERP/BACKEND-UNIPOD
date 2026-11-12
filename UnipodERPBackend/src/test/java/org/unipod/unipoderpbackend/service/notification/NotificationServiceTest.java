package org.unipod.unipoderpbackend.service.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unipod.unipoderpbackend.exception.notification.NotificationNotFoundException;
import org.unipod.unipoderpbackend.model.notification.Notification;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.repository.notification.NotificationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service =
            new NotificationService(repository, Clock.fixed(NOW, ZoneId.of("Africa/Kampala")));

    @Test
    @SuppressWarnings("unchecked")
    void publishSavesOneRowPerRecipientWithNormalisedEmails() {
        UUID briefId = UUID.randomUUID();

        service.publish(new NotificationEvent(
                NotificationType.REVIEWER_ASSIGNED,
                Set.of("Faith@Unipod.ac.ug", "faith@unipod.ac.ug ", "kaddu@unipod.ac.ug"),
                "You have a new brief to review",
                null,
                "submission",
                briefId
        ));

        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        List<Notification> rows = saved.getValue();
        assertEquals(2, rows.size());
        assertEquals(Set.of("faith@unipod.ac.ug", "kaddu@unipod.ac.ug"),
                Set.copyOf(rows.stream().map(Notification::getRecipient).toList()));
        assertTrue(rows.stream().allMatch(row -> row.getCreatedAt().equals(NOW)
                && row.getEntityId().equals(briefId)
                && !row.isRead()));
    }

    @Test
    void eventNeedsARecipientAndATitle() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(
                NotificationType.LOW_STOCK, Set.of(), "Low stock", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(
                NotificationType.LOW_STOCK, Set.of("a@b.c"), " ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(
                NotificationType.LOW_STOCK, Set.of("a@b.c"), "x".repeat(201), null, null, null));
    }

    @Test
    void markReadOnSomeoneElsesNotificationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndRecipient(id, "me@unipod.ac.ug")).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> service.markRead("me@unipod.ac.ug", id));
    }

    @Test
    void readingTwiceKeepsTheFirstReadTime() {
        Notification notification = new Notification(UUID.randomUUID(), "me@unipod.ac.ug",
                NotificationType.FAULT_REPORTED, "Printer fault", null, null, null, NOW.minusSeconds(60));
        Instant first = NOW.minusSeconds(30);
        notification.markRead(first);

        notification.markRead(NOW);

        assertEquals(first, notification.getReadAt());
    }
}
