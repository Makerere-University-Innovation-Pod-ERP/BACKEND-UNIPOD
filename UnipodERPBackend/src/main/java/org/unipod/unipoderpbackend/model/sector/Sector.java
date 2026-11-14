package org.unipod.unipoderpbackend.model.sector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sectors")
public class Sector {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "notification_email", nullable = false, length = 320)
    private String notificationEmail;

    @Column(name = "whatsapp_group_url", length = 500)
    private String whatsappGroupUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Sector() {
    }

    public Sector(
            UUID id,
            String name,
            String notificationEmail,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, notificationEmail, null, active, createdAt, updatedAt);
    }

    public Sector(
            UUID id,
            String name,
            String notificationEmail,
            String whatsappGroupUrl,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.notificationEmail = notificationEmail;
        this.whatsappGroupUrl = clean(whatsappGroupUrl);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String name, String notificationEmail, boolean active, Instant updatedAt) {
        update(name, notificationEmail, this.whatsappGroupUrl, active, updatedAt);
    }

    public void update(
            String name,
            String notificationEmail,
            String whatsappGroupUrl,
            boolean active,
            Instant updatedAt
    ) {
        this.name = name;
        this.notificationEmail = notificationEmail;
        this.whatsappGroupUrl = clean(whatsappGroupUrl);
        this.active = active;
        this.updatedAt = updatedAt;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public String getWhatsappGroupUrl() {
        return whatsappGroupUrl;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
