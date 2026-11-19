package org.unipod.unipoderpbackend.model.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_push_subscriptions")
public class WebPushSubscription {
    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 2048, unique = true)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 128)
    private String p256dh;

    @Column(name = "auth_key", nullable = false, length = 128)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebPushSubscription() {
    }

    public WebPushSubscription(
            UUID id,
            String recipient,
            String endpoint,
            String p256dh,
            String auth,
            Instant createdAt
    ) {
        this.id = id;
        update(recipient, endpoint, p256dh, auth);
        this.createdAt = createdAt;
    }

    public void update(String recipient, String endpoint, String p256dh, String auth) {
        this.recipient = recipient;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public UUID getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
