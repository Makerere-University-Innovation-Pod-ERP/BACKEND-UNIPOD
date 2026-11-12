package org.unipod.unipoderpbackend.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 20)
    private AccountKind accountKind;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "display_first_name", length = 80)
    private String displayFirstName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "student_number", length = 40)
    private String studentNumber;

    @Column(length = 200)
    private String course;

    @Column(name = "university_email", length = 254)
    private String universityEmail;

    @Column(name = "organisation_name", length = 200)
    private String organisationName;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Column(name = "mou_reference", length = 100)
    private String mouReference;

    @Column(name = "rules_version", nullable = false, length = 40)
    private String rulesVersion;

    @Column(name = "rules_accepted_at", nullable = false)
    private Instant rulesAcceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(
            UUID userId,
            AccountKind accountKind,
            String fullName,
            String displayFirstName,
            String phone,
            String rulesVersion,
            Instant now
    ) {
        this.userId = userId;
        this.accountKind = accountKind;
        this.fullName = fullName;
        this.displayFirstName = displayFirstName;
        this.phone = phone;
        this.rulesVersion = rulesVersion;
        this.rulesAcceptedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordStudentDetails(String studentNumber, String course, String universityEmail) {
        this.studentNumber = studentNumber;
        this.course = course;
        this.universityEmail = universityEmail;
    }

    public void recordOrganisationDetails(String organisationName, String contactPerson, String mouReference) {
        this.organisationName = organisationName;
        this.contactPerson = contactPerson;
        this.mouReference = mouReference;
    }

    public UUID getUserId() {
        return userId;
    }

    public AccountKind getAccountKind() {
        return accountKind;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDisplayFirstName() {
        return displayFirstName;
    }

    public String getPhone() {
        return phone;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getCourse() {
        return course;
    }

    public String getUniversityEmail() {
        return universityEmail;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getMouReference() {
        return mouReference;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public Instant getRulesAcceptedAt() {
        return rulesAcceptedAt;
    }
}
