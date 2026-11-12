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
    Page<Notification> findByRecipientOrderByCreatedAtDescIdDesc(String recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadAtIsNullOrderByCreatedAtDescIdDesc(String recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(String recipient);

    Optional<Notification> findByIdAndRecipient(UUID id, String recipient);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification notification
            set notification.readAt = :now
            where notification.recipient = :recipient
              and notification.readAt is null
            """)
    int markAllRead(@Param("recipient") String recipient, @Param("now") Instant now);
}
