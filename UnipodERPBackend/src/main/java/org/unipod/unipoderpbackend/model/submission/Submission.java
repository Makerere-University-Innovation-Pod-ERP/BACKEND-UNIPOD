package org.unipod.unipoderpbackend.model.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.sector.Sector;

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

    @Column(name = "submitter_id", nullable = false)
    private String submitterId;

    @Column(name = "receiving_officer")
    private String receivingOfficer;

    @Column(name = "assigned_reviewer_subject")
    private String assignedReviewerSubject;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_sector_id", insertable = false, updatable = false)
    private Sector responsibleSector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionStage stage;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "forwarded_at")
    private Instant forwardedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_status", nullable = false, length = 24)
    private OutcomeStatus outcomeStatus = OutcomeStatus.NOT_STARTED;

    @Column(name = "outcome_decision", length = 120)
    private String outcomeDecision;

    @Column(name = "outcome_note", columnDefinition = "text")
    private String outcomeNote;

    @Column(name = "outcome_decided_by")
    private String outcomeDecidedBy;

    @Column(name = "outcome_decided_at")
    private Instant outcomeDecidedAt;

    @Column(name = "information_requested_note", columnDefinition = "text")
    private String informationRequestedNote;

    @Column(name = "information_requested_by")
    private String informationRequestedBy;

    @Column(name = "information_requested_at")
    private Instant informationRequestedAt;

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
            String submitterId,
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
        this.submitterId = submitterId;
        this.studentNumber = studentNumber;
        this.programme = programme;
        this.supervisor = supervisor;
        this.recommendationLetterStatus = recommendationLetterStatus;
        this.stage = SubmissionStage.DRAFT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
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
        this(id, category, title, purpose, problemAddressed, registeredBy, registeredBy,
                studentNumber, programme, supervisor, recommendationLetterStatus, createdAt);
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
        if (this.code == null) {
            this.code = caseCode;
        } else if (!this.code.equals(caseCode)) {
            throw new SubmissionConflictException("A submission tracking reference cannot be changed.");
        }
        this.receivedAt = time;
        this.receivingOfficer = officer;
        this.stage = SubmissionStage.RECEIVED;
        this.updatedAt = time;
        this.informationRequestedNote = null;
        this.informationRequestedBy = null;
        this.informationRequestedAt = null;
    }

    public void assignTrackingReference(String caseCode) {
        if (stage != SubmissionStage.DRAFT || code != null || isBlank(caseCode)) {
            throw new SubmissionConflictException("A tracking reference cannot be assigned in the current state.");
        }
        this.code = caseCode;
    }

    public void updateDraft(
            String title,
            String purpose,
            String problemAddressed,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            Instant time
    ) {
        if (stage != SubmissionStage.DRAFT) {
            throw new SubmissionConflictException("Only a draft submission can be updated.");
        }
        this.title = title;
        this.purpose = purpose;
        this.problemAddressed = problemAddressed;
        this.studentNumber = studentNumber;
        this.programme = programme;
        this.supervisor = supervisor;
        this.recommendationLetterStatus = recommendationLetterStatus;
        this.updatedAt = time;
        this.informationRequestedNote = null;
        this.informationRequestedBy = null;
        this.informationRequestedAt = null;
    }

    public void requestInformation(String note, String actor, Instant time) {
        if (stage != SubmissionStage.DRAFT) {
            throw new SubmissionConflictException("Information can only be requested for a draft submission.");
        }
        if (isBlank(note)) {
            throw new InvalidSubmissionException("A reason is required when requesting more information.");
        }
        this.informationRequestedNote = note.trim();
        this.informationRequestedBy = actor;
        this.informationRequestedAt = time;
        this.updatedAt = time;
    }

    public void forward(Sector sector, Instant time) {
        if (stage != SubmissionStage.RECEIVED) {
            throw new SubmissionConflictException("Only received submissions can be forwarded.");
        }
        if (sector == null) {
            throw new InvalidSubmissionException("A sector is required to forward a submission.");
        }
        this.responsibleSectorId = sector.getId();
        this.responsibleSector = sector;
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

    public void transitionTo(SubmissionStage nextStage, Instant time) {
        boolean validTransition = switch (stage) {
            case RECEIVED -> nextStage == SubmissionStage.REGISTERED;
            case REGISTERED -> nextStage == SubmissionStage.FACT_FINDING;
            case FACT_FINDING, FORWARDED -> nextStage == SubmissionStage.UNDER_REVIEW;
            case UNDER_REVIEW, IN_PROGRESS -> nextStage == SubmissionStage.REFINEMENT_REQUESTED;
            case REFINEMENT_REQUESTED -> nextStage == SubmissionStage.FURTHER_HANDLING;
            case FURTHER_HANDLING -> nextStage == SubmissionStage.COMPLETED;
            default -> false;
        };
        if (!validTransition) {
            throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
        }
        this.stage = nextStage;
        this.updatedAt = time;
    }

    public void saveOutcome(String decision, String note, String actor, Instant time) {
        if (stage == SubmissionStage.DRAFT || stage == SubmissionStage.COMPLETED) {
            throw new SubmissionConflictException("The outcome cannot be changed in the current case stage.");
        }
        this.outcomeDecision = clean(decision);
        this.outcomeNote = clean(note);
        this.outcomeStatus = this.outcomeDecision == null
                ? OutcomeStatus.IN_PROGRESS
                : OutcomeStatus.DECIDED;
        this.outcomeDecidedBy = this.outcomeStatus == OutcomeStatus.DECIDED ? actor : null;
        this.outcomeDecidedAt = this.outcomeStatus == OutcomeStatus.DECIDED ? time : null;
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

    public String getSubmitterId() {
        return submitterId;
    }

    public String getReceivingOfficer() {
        return receivingOfficer;
    }

    public String getAssignedReviewerSubject() {
        return assignedReviewerSubject;
    }

    public void assignReviewer(String subject, Instant time) {
        this.assignedReviewerSubject = subject == null || subject.isBlank() ? null : subject.trim();
        this.updatedAt = time;
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

    public String getResponsibleSectorName() {
        return responsibleSector == null ? null : responsibleSector.getName();
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

    public OutcomeStatus getOutcomeStatus() {
        return outcomeStatus;
    }

    public String getOutcomeDecision() {
        return outcomeDecision;
    }

    public String getOutcomeNote() {
        return outcomeNote;
    }

    public String getOutcomeDecidedBy() {
        return outcomeDecidedBy;
    }

    public Instant getOutcomeDecidedAt() {
        return outcomeDecidedAt;
    }

    public String getInformationRequestedNote() {
        return informationRequestedNote;
    }

    public String getInformationRequestedBy() {
        return informationRequestedBy;
    }

    public Instant getInformationRequestedAt() {
        return informationRequestedAt;
    }

    public Long getVersion() {
        return version;
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

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
