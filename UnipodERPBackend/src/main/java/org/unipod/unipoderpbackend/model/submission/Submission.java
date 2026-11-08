package org.unipod.unipoderpbackend.model.submission;

import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions")
public class Submission {
    @Id
    private UUID id;

    @Column(unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubmissionCategory category;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String purpose;

    @Column(name = "problem_addressed", nullable = false, columnDefinition = "text")
    private String problemAddressed;

    @Column(name = "registered_by", nullable = false)
    private String registeredBy;

    @Column(name = "receiving_officer")
    private String receivingOfficer;

    @Column(name = "student_number", length = 80)
    private String studentNumber;

    @Column(length = 200)
    private String programme;

    @Column(length = 255)
    private String supervisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_letter_status", length = 24)
    private RecommendationLetterStatus recommendationLetterStatus;

    @Column(name = "responsible_sector_id")
    private UUID responsibleSectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionStage stage;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "forwarded_at")
    private Instant forwardedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Submission() {
    }

    public Submission(
            UUID id,
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String registeredBy,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            Instant createdAt
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.purpose = purpose;
        this.problemAddressed = problemAddressed;
        this.registeredBy = registeredBy;
        this.studentNumber = studentNumber;
        this.programme = programme;
        this.supervisor = supervisor;
        this.recommendationLetterStatus = recommendationLetterStatus;
        this.stage = SubmissionStage.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void register(String caseCode, Instant time, String officer) {
        if (stage != SubmissionStage.DRAFT) {
            throw new SubmissionConflictException("Submission has already been registered.");
        }
        if (category == SubmissionCategory.STUDENT
                && (isBlank(studentNumber) || isBlank(programme) || isBlank(supervisor)
                || recommendationLetterStatus == null)) {
            throw new InvalidSubmissionException(
                    "Student submissions require student number, programme, supervisor, and recommendation-letter status."
            );
        }
        this.code = caseCode;
        this.receivedAt = time;
        this.receivingOfficer = officer;
        this.stage = SubmissionStage.RECEIVED;
        this.updatedAt = time;
    }

    public void forward(UUID sectorId, Instant time) {
        if (stage != SubmissionStage.RECEIVED) {
            throw new SubmissionConflictException("Only received submissions can be forwarded.");
        }
        if (sectorId == null) {
            throw new InvalidSubmissionException("A sector is required to forward a submission.");
        }
        this.responsibleSectorId = sectorId;
        this.forwardedAt = time;
        this.stage = SubmissionStage.FORWARDED;
        this.updatedAt = time;
    }

    public void advanceTo(SubmissionStage nextStage, Instant time) {
        boolean validTransition = (stage == SubmissionStage.FORWARDED
                && nextStage == SubmissionStage.IN_PROGRESS)
                || (stage == SubmissionStage.IN_PROGRESS
                && nextStage == SubmissionStage.COMPLETED);
        if (!validTransition) {
            throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
        }
        this.stage = nextStage;
        this.updatedAt = time;
    }

    public void touch(Instant time) {
        if (stage == SubmissionStage.COMPLETED) {
            throw new SubmissionConflictException("Completed submissions cannot be changed.");
        }
        this.updatedAt = time;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public SubmissionCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getProblemAddressed() {
        return problemAddressed;
    }

    public String getRegisteredBy() {
        return registeredBy;
    }

    public String getReceivingOfficer() {
        return receivingOfficer;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getProgramme() {
        return programme;
    }

    public String getSupervisor() {
        return supervisor;
    }

    public RecommendationLetterStatus getRecommendationLetterStatus() {
        return recommendationLetterStatus;
    }

    public UUID getResponsibleSectorId() {
        return responsibleSectorId;
    }

    public SubmissionStage getStage() {
        return stage;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getForwardedAt() {
        return forwardedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
