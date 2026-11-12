package org.unipod.unipoderpbackend.model.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.partner.InvalidPartnerException;

@Entity
@Table(name = "partners")
public class Partner {
    private static final int NAME_MAX = 255;
    private static final int PERSON_MAX = 255;
    private static final int EMAIL_MAX = 254;
    private static final int NOTES_MAX = 2000;

    @Id
    private UUID id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartnerType type;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "contact_person", length = PERSON_MAX)
    private String contactPerson;

    @Column(name = "contact_email", length = EMAIL_MAX)
    private String contactEmail;

    @Column(length = NOTES_MAX)
    private String notes;

    @Column(name = "document_reference", length = 500)
    private String documentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_notified_status", length = 20)
    private PartnerStatus lastNotifiedStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Partner() {
    }

    public Partner(
            UUID id,
            String name,
            PartnerType type,
            LocalDate renewalDate,
            String contactPerson,
            String contactEmail,
            String notes,
            Instant now) {
        if (id == null || now == null) {
            throw new IllegalArgumentException("A partner needs an id and a creation time.");
        }
        if (type == null) {
            throw new InvalidPartnerException("Partner type is required.");
        }
        this.id = id;
        this.name = requiredText(name, "Partner name", NAME_MAX);
        this.type = type;
        this.renewalDate = renewalDate;
        this.contactPerson = optionalText(contactPerson, "Contact person", PERSON_MAX);
        this.contactEmail = optionalText(contactEmail, "Contact email", EMAIL_MAX);
        this.notes = optionalText(notes, "Notes", NOTES_MAX);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies only the details that are present. A blank contact person, email or note clears it.
     * The renewal date can be replaced but not cleared, because an omitted value means "unchanged".
     */
    public void updateDetails(
            String name,
            PartnerType type,
            LocalDate renewalDate,
            String contactPerson,
            String contactEmail,
            String notes,
            Instant now) {
        String newName = name == null ? this.name : requiredText(name, "Partner name", NAME_MAX);
        String newContactPerson = contactPerson == null
                ? this.contactPerson : optionalText(contactPerson, "Contact person", PERSON_MAX);
        String newContactEmail = contactEmail == null
                ? this.contactEmail : optionalText(contactEmail, "Contact email", EMAIL_MAX);
        String newNotes = notes == null ? this.notes : optionalText(notes, "Notes", NOTES_MAX);
        this.name = newName;
        this.contactPerson = newContactPerson;
        this.contactEmail = newContactEmail;
        this.notes = newNotes;
        if (type != null) {
            this.type = type;
        }
        if (renewalDate != null) {
            this.renewalDate = renewalDate;
        }
        this.updatedAt = now;
    }

    // Blank optional text is stored as null so "no value" has one representation.
    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, field, maxLength);
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidPartnerException(field + " is required.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new InvalidPartnerException(field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PartnerType getType() {
        return type;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getNotes() {
        return notes;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public PartnerStatus getLastNotifiedStatus() {
        return lastNotifiedStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
