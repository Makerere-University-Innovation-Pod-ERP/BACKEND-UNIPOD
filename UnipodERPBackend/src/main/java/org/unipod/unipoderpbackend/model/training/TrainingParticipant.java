package org.unipod.unipoderpbackend.model.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;

@Entity
@Table(name = "training_participants")
public class TrainingParticipant {
    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "reg_number", nullable = false, length = 50)
    private String regNumber;

    @Column(nullable = false, length = 200)
    private String department;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus attendance;

    @Column(nullable = false)
    private boolean passed;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingParticipant() {
    }

    /**
     * Registers someone for a session. A free session confirms the seat at once; a paid one holds
     * it as PENDING_PAYMENT until payment is confirmed (D-018).
     *
     * @param sessionFee the session's fee in whole UGX
     */
    public TrainingParticipant(
            UUID id,
            UUID sessionId,
            String name,
            String regNumber,
            String department,
            String contactEmail,
            int sessionFee,
            Instant now
    ) {
        if (id == null || sessionId == null || now == null) {
            throw new InvalidTrainingException("A registration needs a session.");
        }
        this.id = id;
        this.sessionId = sessionId;
        this.name = required(name, "name");
        this.regNumber = required(regNumber, "registration number");
        this.department = required(department, "department");
        this.contactEmail = contactEmail == null || contactEmail.isBlank() ? null : contactEmail.strip();
        this.status = sessionFee > 0 ? RegistrationStatus.PENDING_PAYMENT : RegistrationStatus.CONFIRMED;
        this.attendance = AttendanceStatus.UNMARKED;
        this.passed = false;
        this.registeredAt = now;
        this.updatedAt = now;
    }

    public boolean occupiesSeat() {
        return status != RegistrationStatus.CANCELLED;
    }

    public void confirmPayment(Instant now) {
        if (status != RegistrationStatus.PENDING_PAYMENT) {
            throw new TrainingConflictException("This registration is not waiting for payment.");
        }
        status = RegistrationStatus.CONFIRMED;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == RegistrationStatus.CANCELLED) {
            throw new TrainingConflictException("This registration is already cancelled.");
        }
        if (passed) {
            throw new TrainingConflictException("A participant who has passed cannot be removed.");
        }
        status = RegistrationStatus.CANCELLED;
        updatedAt = now;
    }

    public void markAttendance(AttendanceStatus newAttendance, Instant now) {
        if (status != RegistrationStatus.CONFIRMED) {
            throw new TrainingConflictException("Attendance can only be recorded for confirmed participants.");
        }
        // A pass is only valid for someone who attended; the database enforces the same rule.
        if (passed && newAttendance != AttendanceStatus.PRESENT) {
            throw new TrainingConflictException("A participant who has passed must stay marked present.");
        }
        attendance = newAttendance;
        updatedAt = now;
    }

    public void markPassed(boolean sessionIsSafetyCourse, Instant now) {
        if (!sessionIsSafetyCourse) {
            throw new TrainingConflictException("Only safety courses record a pass.");
        }
        if (status != RegistrationStatus.CONFIRMED || attendance != AttendanceStatus.PRESENT) {
            throw new TrainingConflictException("A participant must be marked present before passing.");
        }
        if (passed) {
            throw new TrainingConflictException("This participant has already passed.");
        }
        passed = true;
        updatedAt = now;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrainingException("The participant " + field + " is required.");
        }
        return value.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getName() {
        return name;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public String getDepartment() {
        return department;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public AttendanceStatus getAttendance() {
        return attendance;
    }

    public boolean isPassed() {
        return passed;
    }

    public long getVersion() {
        return version;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
