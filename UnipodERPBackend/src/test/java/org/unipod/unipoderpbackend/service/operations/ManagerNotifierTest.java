package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.ManagerRecipientQuery;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

class ManagerNotifierTest {
    private final ManagerRecipientQuery recipients = mock(ManagerRecipientQuery.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ManagerNotifier notifier = new ManagerNotifier(recipients, notifications);

    @Test
    void tellsAdministratorsAndTheDepartmentsCoordinatorsOnceEach() {
        when(recipients.administratorEmails()).thenReturn(List.of("admin@test.local", "both@test.local"));
        when(recipients.coordinatorEmails(anyCollection())).thenReturn(List.of("both@test.local", "coord@test.local"));

        List<String> told = notifier.notifyManagers(DepartmentCode.TEXTILE, NotificationType.FAULT_REPORTED, "Fault",
                "Body", "machine", UUID.randomUUID());

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notifications).publish(event.capture());
        assertEquals(3, event.getValue().recipients().size());
        assertEquals(List.of("Administrators", "Textile coordinators"), told);
    }

    @Test
    void nobodyToTellMeansNothingIsPublished() {
        when(recipients.administratorEmails()).thenReturn(List.of());
        when(recipients.coordinatorEmails(anyCollection())).thenReturn(List.of());

        List<String> told = notifier.notifyManagers(DepartmentCode.CNC, NotificationType.LOW_STOCK, "Low", "Body",
                "stock_item", UUID.randomUUID());

        assertTrue(told.isEmpty());
        verify(notifications, never()).publish(any());
    }

    @Test
    void aLongTitleIsCutWithoutSplittingAnEmoji() {
        when(recipients.administratorEmails()).thenReturn(List.of("admin@test.local"));
        when(recipients.coordinatorEmails(anyCollection())).thenReturn(List.of());
        String title = "x".repeat(199) + "\uD83D\uDE00 and more";

        notifier.notifyManagers(DepartmentCode.CNC, NotificationType.LOW_STOCK, title, "Body", "stock_item", UUID.randomUUID());

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notifications).publish(event.capture());
        assertEquals("x".repeat(199), event.getValue().title());
        assertFalse(Character.isHighSurrogate(event.getValue().title().charAt(event.getValue().title().length() - 1)));
    }
}
