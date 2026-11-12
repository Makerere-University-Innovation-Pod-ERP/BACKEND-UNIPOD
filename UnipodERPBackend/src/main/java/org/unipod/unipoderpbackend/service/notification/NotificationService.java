package org.unipod.unipoderpbackend.service.notification;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.notification.NotificationResponse;
import org.unipod.unipoderpbackend.exception.notification.NotificationNotFoundException;
import org.unipod.unipoderpbackend.model.notification.Notification;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.repository.notification.NotificationRepository;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(NotificationEvent event) {
        List<Notification> rows = event.recipients().stream()
                .map(recipient -> new Notification(
                        UUID.randomUUID(),
                        recipient,
                        event.type(),
                        event.title(),
                        event.body(),
                        event.entityType(),
                        event.entityId(),
                        clock.instant()
                ))
                .toList();
        notifications.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String recipient, boolean unreadOnly, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return PageResponse.from((unreadOnly
                ? notifications.findByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(recipient, pageRequest)
                : notifications.findByRecipientOrderByCreatedAtDescIdDesc(recipient, pageRequest))
                .map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String recipient) {
        return notifications.countByRecipientAndReadAtIsNull(recipient);
    }

    @Transactional
    public NotificationResponse markRead(String recipient, UUID id) {
        Notification notification = notifications.findByIdAndRecipient(id, recipient)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(String recipient) {
        return notifications.markAllRead(recipient, clock.instant());
    }
}
