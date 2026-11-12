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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.unipod.unipoderpbackend.exception.partner.InvalidPartnerException;

// Only changed columns are written, so an edit never overwrites last_notified_status, which the
// daily job updates on its own (see PartnerRepository.claimNotification).
@Entity
@DynamicUpdate
@Table(name = "partners")
public class Partner {
    // Keeps dates inside what the database can store and what staff could plausibly enter.
    private static final LocalDate EARLIEST_RENEWAL = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST_RENEWAL = LocalDate.of(2999, 12, 31);
    private static final int NAME_MAX = 255;
    private static final int PERSON_MAX = 255;
    private static final int EMAIL_MAX = 254;
    private static final int NOTES_MAX = 2000;
    private static final int DOCUMENT_REFERENCE_MAX = 500;

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

    @Column(name = "document_reference", length = DOCUMENT_REFERENCE_MAX)
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
        this.renewalDate = checkedRenewalDate(renewalDate);
        this.contactPerson = optionalText(contactPerson, "Contact person", PERSON_MAX);
        this.contactEmail = optionalText(contactEmail, "Contact email", EMAIL_MAX);
        this.notes = optionalText(notes, "Notes", NOTES_MAX);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applies only the details that are present and returns whether anything changed; when nothing
     * did, the partner is left exactly as it was. A blank contact person, email or note clears it.
     * The renewal date can be replaced but not cleared, because an omitted value means "unchanged".
     * A different renewal date forgets the status already announced, so the new date gets its own
     * warnings.
     */
    public boolean updateDetails(
            String name,
            PartnerType type,
            LocalDate renewalDate,
            String contactPerson,
            String contactEmail,
            String notes,
            Instant now) {
        String newName = name == null ? this.name : requiredText(name, "Partner name", NAME_MAX);
        PartnerType newType = type == null ? this.type : type;
        LocalDate newRenewalDate = renewalDate == null ? this.renewalDate : checkedRenewalDate(renewalDate);
        String newContactPerson = contactPerson == null
                ? this.contactPerson : optionalText(contactPerson, "Contact person", PERSON_MAX);
        String newContactEmail = contactEmail == null
                ? this.contactEmail : optionalText(contactEmail, "Contact email", EMAIL_MAX);
        String newNotes = notes == null ? this.notes : optionalText(notes, "Notes", NOTES_MAX);
        boolean unchanged = Objects.equals(newName, this.name)
                && newType == this.type
                && Objects.equals(newRenewalDate, this.renewalDate)
                && Objects.equals(newContactPerson, this.contactPerson)
                && Objects.equals(newContactEmail, this.contactEmail)
                && Objects.equals(newNotes, this.notes);
        if (unchanged) {
            return false;
        }
        if (!Objects.equals(newRenewalDate, this.renewalDate)) {
            this.lastNotifiedStatus = null;
        }
        this.name = newName;
        this.type = newType;
        this.renewalDate = newRenewalDate;
        this.contactPerson = newContactPerson;
        this.contactEmail = newContactEmail;
        this.notes = newNotes;
        this.updatedAt = now;
        return true;
    }

    /**
     * Records a renewed MOU. The document reference is replaced, so omitting it clears the old one.
     * A different renewal date forgets the status already announced, so the new date gets its own
     * warnings.
     */
    public void renewMou(LocalDate renewalDate, String documentReference, Instant now) {
        if (renewalDate == null) {
            throw new InvalidPartnerException("Renewal date is required.");
        }
        String newDocumentReference = optionalText(documentReference, "Document reference", DOCUMENT_REFERENCE_MAX);
        LocalDate newRenewalDate = checkedRenewalDate(renewalDate);
        if (!newRenewalDate.equals(this.renewalDate)) {
            this.lastNotifiedStatus = null;
        }
        this.documentReference = newDocumentReference;
        this.renewalDate = newRenewalDate;
        this.updatedAt = now;
    }

    private static LocalDate checkedRenewalDate(LocalDate date) {
        if (date != null && (date.isBefore(EARLIEST_RENEWAL) || date.isAfter(LATEST_RENEWAL))) {
            throw new InvalidPartnerException(
                    "Renewal date must be between " + EARLIEST_RENEWAL.getYear() + " and "
                            + LATEST_RENEWAL.getYear() + ".");
        }
        return date;
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
        if (value.indexOf('\0') >= 0) {
            throw new InvalidPartnerException(field + " must not contain null characters.");
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
