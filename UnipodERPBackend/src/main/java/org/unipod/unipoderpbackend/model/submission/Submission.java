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

    @Column(name = "submitter_email", length = 254)
    private String submitterEmail;

    @Column(name = "submitter_name", length = 255)
    private String submitterName;

    @Column(name = "submitter_phone", length = 40)
    private String submitterPhone;

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

    @Column(name = "service_requested", length = 255)
    private String serviceRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_requester", length = 32)
    private ServiceRequester serviceRequester;

    @Column(length = 255)
    private String department;

    @Column(name = "expected_result", columnDefinition = "text")
    private String expectedResult;

    @Column(name = "project_code", unique = true, length = 32)
    private String projectCode;

    @Column(name = "brief_version", nullable = false)
    private int briefVersion = 1;

    @Column(name = "accepted_rules_version", length = 32)
    private String acceptedRulesVersion;

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

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "forwarded_at")
    private Instant forwardedAt;

    @Column(name = "forward_note", columnDefinition = "text")
    private String forwardNote;

    @Column(name = "forwarded_by")
    private String forwardedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

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
        this(id, category, title, purpose, problemAddressed, registeredBy, submitterId, studentNumber,
                programme, supervisor, recommendationLetterStatus, null, createdAt);
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
            String submitterEmail,
            Instant createdAt
    ) {
        this(id, category, title, purpose, problemAddressed, registeredBy, submitterId, studentNumber,
                programme, supervisor, recommendationLetterStatus, submitterEmail, null, createdAt);
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
            String submitterEmail,
            String submitterName,
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
        this.submitterEmail = submitterEmail;
        this.submitterName = submitterName;
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
        requireStudentDetails();
        if (this.code == null) {
            this.code = caseCode;
        } else if (!this.code.equals(caseCode)) {
            throw new SubmissionConflictException("A submission tracking reference cannot be changed.");
        }
        this.receivedAt = time;
        this.submittedAt = this.submittedAt == null ? time : this.submittedAt;
        this.receivingOfficer = officer;
        this.stage = SubmissionStage.RECEIVED;
        this.updatedAt = time;
        clearInformationRequest();
    }

    /**
     * Moves a draft into the submitter-facing intake queue. The tracking
     * reference is allocated on submit (it is also allocated on create for the
     * assisted/legacy flow).
     */
    public void submit(String caseCode, Instant time) {
        if (stage != SubmissionStage.DRAFT) {
            throw new SubmissionConflictException("Only a draft submission can be submitted.");
        }
        requireStudentDetails();
        requireServiceRequestDetails();
        if (this.code == null) {
            this.code = caseCode;
        }
        this.receivedAt = time;
        this.submittedAt = time;
        this.stage = SubmissionStage.SUBMITTED;
        this.updatedAt = time;
        clearInformationRequest();
    }

    /**
     * Records the submitter's response to an information request and returns
     * the brief to the intake queue as a new brief version.
     */
    public void reply(Instant time) {
        if (stage != SubmissionStage.AWAITING_INFORMATION) {
            throw new SubmissionConflictException("Only a submission awaiting information can be replied to.");
        }
        requireStudentDetails();
        requireServiceRequestDetails();
        this.briefVersion = Math.max(1, this.briefVersion) + 1;
        this.submittedAt = time;
        this.stage = SubmissionStage.SUBMITTED;
        this.updatedAt = time;
        clearInformationRequest();
    }

    /**
     * Applies the fields a submitter supplies when answering an information
     * request. Only non-null values are applied so a submitter can send just the
     * detail reception asked for.
     */
    public void applyReplyDetails(
            String title,
            String purpose,
            String problemAddressed,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            String submitterPhone,
            String serviceRequested,
            String department,
            String expectedResult,
            Instant time
    ) {
        if (stage != SubmissionStage.AWAITING_INFORMATION) {
            throw new SubmissionConflictException("Only a submission awaiting information can be updated by its submitter.");
        }
        if (title != null) {
            this.title = title.trim();
        }
        if (purpose != null) {
            this.purpose = purpose.trim();
        }
        if (problemAddressed != null) {
            this.problemAddressed = problemAddressed.trim();
        }
        if (studentNumber != null) {
            this.studentNumber = clean(studentNumber);
        }
        if (programme != null) {
            this.programme = clean(programme);
        }
        if (supervisor != null) {
            this.supervisor = clean(supervisor);
        }
        if (recommendationLetterStatus != null) {
            this.recommendationLetterStatus = recommendationLetterStatus;
        }
        if (submitterPhone != null) {
            this.submitterPhone = clean(submitterPhone);
        }
        if (serviceRequested != null) {
            this.serviceRequested = clean(serviceRequested);
        }
        if (department != null) {
            this.department = clean(department);
        }
        if (expectedResult != null) {
            this.expectedResult = clean(expectedResult);
        }
        this.updatedAt = time;
    }

    public void clearInformationRequest() {
        this.informationRequestedNote = null;
        this.informationRequestedBy = null;
        this.informationRequestedAt = null;
    }

    public void updateSubmitterEmail(String submitterEmail) {
        this.submitterEmail = submitterEmail;
    }

    public void updateSubmitterName(String submitterName) {
        this.submitterName = submitterName == null || submitterName.isBlank() ? null : submitterName.trim();
    }

    public void applyIntakeDetails(
            String submitterPhone,
            String serviceRequested,
            ServiceRequester serviceRequester,
            String department,
            String expectedResult,
            String acceptedRulesVersion
    ) {
        this.submitterPhone = clean(submitterPhone);
        this.serviceRequested = clean(serviceRequested);
        this.serviceRequester = serviceRequester;
        this.department = clean(department);
        this.expectedResult = clean(expectedResult);
        this.acceptedRulesVersion = clean(acceptedRulesVersion);
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
    }

    public void requestInformation(String note, String actor, Instant time) {
        if (stage != SubmissionStage.DRAFT && stage != SubmissionStage.SUBMITTED) {
            throw new SubmissionConflictException("Information can only be requested for a draft or submitted submission.");
        }
        if (isBlank(note)) {
            throw new InvalidSubmissionException("A reason is required when requesting more information.");
        }
        this.informationRequestedNote = note.trim();
        this.informationRequestedBy = actor;
        this.informationRequestedAt = time;
        if (stage == SubmissionStage.SUBMITTED) {
            this.stage = SubmissionStage.AWAITING_INFORMATION;
        }
        this.updatedAt = time;
    }

    public void forward(Sector sector, Instant time) {
        forward(sector, null, null, time);
    }

    public void forward(Sector sector, String note, String actor, Instant time) {
        if (stage != SubmissionStage.RECEIVED && stage != SubmissionStage.SUBMITTED) {
            throw new SubmissionConflictException("Only received or submitted submissions can be forwarded.");
        }
        if (sector == null) {
            throw new InvalidSubmissionException("A sector is required to forward a submission.");
        }
        this.responsibleSectorId = sector.getId();
        this.responsibleSector = sector;
        this.forwardedAt = time;
        this.forwardNote = clean(note);
        this.forwardedBy = clean(actor);
        if (this.receivingOfficer == null) {
            this.receivingOfficer = clean(actor);
        }
        this.stage = SubmissionStage.FORWARDED;
        this.updatedAt = time;
    }

    public void confirmAsProject(String projectCode, String note, Instant time) {
        confirmAsProject(projectCode, note, null, time);
    }

    public void confirmAsProject(String projectCode, String note, String actor, Instant time) {
        boolean confirmable = stage == SubmissionStage.SUBMITTED
                || stage == SubmissionStage.RECEIVED
                || stage == SubmissionStage.FORWARDED
                || stage == SubmissionStage.FACT_FINDING;
        if (!confirmable) {
            throw new SubmissionConflictException("This submission cannot be confirmed as a project in its current stage.");
        }
        if (isBlank(projectCode)) {
            throw new InvalidSubmissionException("A project code is required to confirm a project.");
        }
        this.projectCode = projectCode.trim();
        this.forwardNote = clean(note) == null ? this.forwardNote : clean(note);
        if (this.receivingOfficer == null) {
            this.receivingOfficer = clean(actor);
        }
        this.confirmedAt = time;
        this.stage = SubmissionStage.CONFIRMED_AS_PROJECT;
        this.updatedAt = time;
    }

    public void reject(String reason, Instant time) {
        boolean rejectable = stage == SubmissionStage.SUBMITTED
                || stage == SubmissionStage.AWAITING_INFORMATION
                || stage == SubmissionStage.RECEIVED
                || stage == SubmissionStage.FORWARDED;
        if (!rejectable) {
            throw new SubmissionConflictException("This submission cannot be rejected in its current stage.");
        }
        if (isBlank(reason)) {
            throw new InvalidSubmissionException("A reason is required to reject a submission.");
        }
        this.rejectionReason = reason.trim();
        this.stage = SubmissionStage.REJECTED;
        this.updatedAt = time;
        clearInformationRequest();
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
            case SUBMITTED -> nextStage == SubmissionStage.REGISTERED;
            case RECEIVED -> nextStage == SubmissionStage.REGISTERED;
            case REGISTERED -> nextStage == SubmissionStage.FACT_FINDING;
            case FACT_FINDING, FORWARDED -> nextStage == SubmissionStage.UNDER_REVIEW
                    || nextStage == SubmissionStage.REFINEMENT_REQUESTED;
            case CONFIRMED_AS_PROJECT -> nextStage == SubmissionStage.FACT_FINDING;
            case UNDER_REVIEW, IN_PROGRESS -> nextStage == SubmissionStage.REFINEMENT_REQUESTED;
            case REFINEMENT_REQUESTED -> nextStage == SubmissionStage.FURTHER_HANDLING;
            case FURTHER_HANDLING -> nextStage == SubmissionStage.COMPLETED;
            case DRAFT, AWAITING_INFORMATION, REJECTED, COMPLETED -> false;
        };
        if (!validTransition) {
            throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
        }
        this.stage = nextStage;
        this.updatedAt = time;
    }

    public void saveOutcome(String decision, String note, String actor, Instant time) {
        if (stage == SubmissionStage.DRAFT
                || stage == SubmissionStage.REJECTED
                || stage == SubmissionStage.COMPLETED) {
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
        if (stage == SubmissionStage.COMPLETED || stage == SubmissionStage.REJECTED) {
            throw new SubmissionConflictException("Closed submissions cannot be changed.");
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

    public String getSubmitterEmail() {
        return submitterEmail;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public String getSubmitterPhone() {
        return submitterPhone;
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

    public String getServiceRequested() {
        return serviceRequested;
    }

    public ServiceRequester getServiceRequester() {
        return serviceRequester;
    }

    public String getDepartment() {
        return department;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public int getBriefVersion() {
        return briefVersion;
    }

    public String getAcceptedRulesVersion() {
        return acceptedRulesVersion;
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getForwardedAt() {
        return forwardedAt;
    }

    public String getForwardNote() {
        return forwardNote;
    }

    public String getForwardedBy() {
        return forwardedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
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

    private void requireStudentDetails() {
        if (category == SubmissionCategory.STUDENT
                && (isBlank(studentNumber) || isBlank(programme) || isBlank(supervisor)
                || recommendationLetterStatus == null)) {
            throw new InvalidSubmissionException(
                    "Student submissions require student number, programme, supervisor, and recommendation-letter status."
            );
        }
    }

    private void requireServiceRequestDetails() {
        if (category == SubmissionCategory.SERVICE_REQUEST
                && (isBlank(serviceRequested) || isBlank(expectedResult))) {
            throw new InvalidSubmissionException(
                    "Service requests require the service requested and the expected result."
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
