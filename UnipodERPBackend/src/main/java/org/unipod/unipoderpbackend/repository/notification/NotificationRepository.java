package org.unipod.unipoderpbackend.repository.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.notification.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("""
            select notification from Notification notification
            where notification.recipient = :recipient
              and (:includeBookings = true
                   or notification.entityType is null
                   or lower(notification.entityType) <> 'booking')
              and (:unreadOnly = false or notification.readAt is null)
            order by notification.createdAt desc, notification.id desc
            """)
    Page<Notification> findVisibleByRecipient(
            @Param("recipient") String recipient,
            @Param("includeBookings") boolean includeBookings,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    @Query("""
            select count(notification) from Notification notification
            where notification.recipient = :recipient
              and notification.readAt is null
              and (:includeBookings = true
                   or notification.entityType is null
                   or lower(notification.entityType) <> 'booking')
            """)
    long countUnreadVisibleByRecipient(
            @Param("recipient") String recipient,
            @Param("includeBookings") boolean includeBookings
    );

    @Query("""
            select notification from Notification notification
            where notification.id = :id
              and notification.recipient = :recipient
              and (:includeBookings = true
                   or notification.entityType is null
                   or lower(notification.entityType) <> 'booking')
            """)
    Optional<Notification> findVisibleByIdAndRecipient(
            @Param("id") UUID id,
            @Param("recipient") String recipient,
            @Param("includeBookings") boolean includeBookings
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification notification
            set notification.readAt = :now
            where notification.recipient = :recipient
              and notification.readAt is null
              and (:includeBookings = true
                   or notification.entityType is null
                   or lower(notification.entityType) <> 'booking')
            """)
    int markAllRead(
            @Param("recipient") String recipient,
            @Param("includeBookings") boolean includeBookings,
            @Param("now") Instant now
    );
}
