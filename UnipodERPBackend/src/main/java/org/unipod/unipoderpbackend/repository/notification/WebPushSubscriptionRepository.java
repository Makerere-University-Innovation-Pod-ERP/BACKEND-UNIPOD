package org.unipod.unipoderpbackend.repository.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.notification.WebPushSubscription;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {
    List<WebPushSubscription> findByRecipient(String recipient);

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    Optional<WebPushSubscription> findByRecipientAndEndpoint(String recipient, String endpoint);
}
