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

    @Column(name = "preferred_at")
    private Instant preferredAt;

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
        requireServiceRequestDetails();
        requireAcceptedRules();
        if (this.code == null) {
            this.code = caseCode;
        } else if (!this.code.equals(caseCode)) {
            throw new SubmissionConflictException("A submission tracking reference cannot be changed.");
        }
        this.receivedAt = time;
        this.submittedAt = this.submittedAt == null ? time : this.submittedAt;
        this.receivingOfficer = officer;
        this.stage = SubmissionStage.SUBMITTED;
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
        requireBriefContent();
        requireStudentDetails();
        requireServiceRequestDetails();
        requireContactDetails();
        requireAcceptedRules();
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
        boolean informationReply = stage == SubmissionStage.AWAITING_INFORMATION
                || stage == SubmissionStage.SUBMITTED;
        boolean improvementReply = stage == SubmissionStage.IMPROVEMENT_REQUESTED;
        if (!informationReply && !improvementReply) {
            throw new SubmissionConflictException(
                    "Only a submitted, awaiting-information or improvement-requested submission can be replied to."
            );
        }
        requireStudentDetails();
        requireServiceRequestDetails();
        requireContactDetails();
        this.briefVersion = Math.max(1, this.briefVersion) + 1;
        this.submittedAt = time;
        this.stage = improvementReply ? SubmissionStage.RESUBMITTED : SubmissionStage.SUBMITTED;
        if (improvementReply) {
            // The improvement decision stays in the stage history and audit trail;
            // reopening the outcome lets the next review round record its decision.
            this.outcomeStatus = OutcomeStatus.IN_PROGRESS;
        }
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
            Instant preferredAt,
            Instant time
    ) {
        if (stage != SubmissionStage.AWAITING_INFORMATION
                && stage != SubmissionStage.SUBMITTED
                && stage != SubmissionStage.IMPROVEMENT_REQUESTED) {
            throw new SubmissionConflictException(
                    "Only a submitted, awaiting-information or improvement-requested submission can be updated by its submitter."
            );
        }
        requireReplyDetailsMatchCategory(
                studentNumber,
                programme,
                supervisor,
                recommendationLetterStatus,
                serviceRequested,
                department,
                expectedResult
        );
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
        if (preferredAt != null) {
            this.preferredAt = preferredAt;
        }
        this.updatedAt = time;
    }

    private void requireReplyDetailsMatchCategory(
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            String serviceRequested,
            String department,
            String expectedResult
    ) {
        boolean studentDetails = clean(studentNumber) != null
                || clean(programme) != null
                || clean(supervisor) != null
                || recommendationLetterStatus != null;
        boolean serviceDetails = clean(serviceRequested) != null
                || clean(department) != null
                || clean(expectedResult) != null;
        if (category == SubmissionCategory.STUDENT && serviceDetails) {
            throw new InvalidSubmissionException(
                    "Student submissions cannot carry service-request fields."
            );
        }
        if (category == SubmissionCategory.SERVICE_REQUEST && studentDetails) {
            throw new InvalidSubmissionException(
                    "Service-request submissions cannot carry student fields."
            );
        }
        if (category != SubmissionCategory.STUDENT
                && category != SubmissionCategory.SERVICE_REQUEST
                && (studentDetails || serviceDetails)) {
            throw new InvalidSubmissionException(
                    "This submission category does not accept student or service-request fields."
            );
        }
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
            Instant preferredAt,
            String acceptedRulesVersion
    ) {
        this.submitterPhone = clean(submitterPhone);
        this.serviceRequested = clean(serviceRequested);
        this.serviceRequester = serviceRequester;
        this.department = clean(department);
        this.expectedResult = clean(expectedResult);
        this.preferredAt = preferredAt;
        if (acceptedRulesVersion != null) {
            this.acceptedRulesVersion = clean(acceptedRulesVersion);
        }
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

    /**
     * Applies the fields present in an autosave request. Only non-null values are
     * applied, so the editor can send the single field that changed.
     */
    public void patchDraft(
            String title,
            String purpose,
            String problemAddressed,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            String submitterPhone,
            String serviceRequested,
            ServiceRequester serviceRequester,
            String department,
            String expectedResult,
            Instant preferredAt,
            String acceptedRulesVersion,
            String submitterEmail,
            String submitterName,
            Instant time
    ) {
        if (stage != SubmissionStage.DRAFT) {
            throw new SubmissionConflictException("Only a draft submission can be updated.");
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
        if (serviceRequester != null) {
            this.serviceRequester = serviceRequester;
        }
        if (department != null) {
            this.department = clean(department);
        }
        if (expectedResult != null) {
            this.expectedResult = clean(expectedResult);
        }
        if (preferredAt != null) {
            this.preferredAt = preferredAt;
        }
        if (acceptedRulesVersion != null) {
            this.acceptedRulesVersion = clean(acceptedRulesVersion);
        }
        if (submitterEmail != null) {
            this.submitterEmail = clean(submitterEmail);
        }
        if (submitterName != null) {
            this.submitterName = clean(submitterName);
        }
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
        if (stage != SubmissionStage.SUBMITTED) {
            throw new SubmissionConflictException("Only a submitted submission can be forwarded.");
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
                || stage == SubmissionStage.FORWARDED
                || stage == SubmissionStage.IN_FACT_FINDING
                || stage == SubmissionStage.READY_FOR_REVIEW
                || stage == SubmissionStage.IN_REVIEW
                || stage == SubmissionStage.IMPROVEMENT_REQUESTED
                || stage == SubmissionStage.RESUBMITTED;
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
                || stage == SubmissionStage.FORWARDED
                || stage == SubmissionStage.IN_FACT_FINDING
                || stage == SubmissionStage.READY_FOR_REVIEW
                || stage == SubmissionStage.IN_REVIEW
                || stage == SubmissionStage.IMPROVEMENT_REQUESTED
                || stage == SubmissionStage.RESUBMITTED
                || stage == SubmissionStage.AWAITING_PAYMENT;
        if (!rejectable) {
            throw new SubmissionConflictException("This submission cannot be closed in its current stage.");
        }
        if (isBlank(reason)) {
            throw new InvalidSubmissionException("A reason is required to close a submission.");
        }
        this.rejectionReason = reason.trim();
        this.stage = SubmissionStage.CLOSED;
        this.updatedAt = time;
        clearInformationRequest();
    }

    public void advanceTo(SubmissionStage nextStage, Instant time) {
        boolean validTransition = (stage == SubmissionStage.CONFIRMED_AS_PROJECT
                && nextStage == SubmissionStage.IN_PROGRESS)
                || (stage == SubmissionStage.AWAITING_PAYMENT
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
            case SUBMITTED -> nextStage == SubmissionStage.FORWARDED;
            case FORWARDED -> nextStage == SubmissionStage.IN_FACT_FINDING
                    || nextStage == SubmissionStage.READY_FOR_REVIEW;
            case IN_FACT_FINDING -> nextStage == SubmissionStage.READY_FOR_REVIEW
                    || nextStage == SubmissionStage.IN_REVIEW
                    || nextStage == SubmissionStage.IMPROVEMENT_REQUESTED;
            case READY_FOR_REVIEW -> nextStage == SubmissionStage.IN_REVIEW
                    || nextStage == SubmissionStage.IMPROVEMENT_REQUESTED
                    || nextStage == SubmissionStage.CLOSED;
            case IN_REVIEW -> nextStage == SubmissionStage.IMPROVEMENT_REQUESTED
                    || nextStage == SubmissionStage.CONFIRMED_AS_PROJECT
                    || nextStage == SubmissionStage.AWAITING_PAYMENT
                    || nextStage == SubmissionStage.CLOSED;
            case IMPROVEMENT_REQUESTED -> nextStage == SubmissionStage.RESUBMITTED;
            case RESUBMITTED -> nextStage == SubmissionStage.IN_REVIEW;
            case CONFIRMED_AS_PROJECT -> nextStage == SubmissionStage.AWAITING_PAYMENT
                    || nextStage == SubmissionStage.IN_PROGRESS;
            case AWAITING_PAYMENT -> nextStage == SubmissionStage.IN_PROGRESS;
            case IN_PROGRESS -> nextStage == SubmissionStage.COMPLETED
                    || nextStage == SubmissionStage.CLOSED;
            case DRAFT, AWAITING_INFORMATION, COMPLETED, CLOSED -> false;
        };
        if (!validTransition) {
            throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
        }
        this.stage = nextStage;
        this.updatedAt = time;
    }

    public void confirmOutcomeAsProject(
            String decision,
            String note,
            String actor,
            String projectCode,
            Sector sector,
            Instant time
    ) {
        requireOutcomeOpen();
        if (isBlank(projectCode)) {
            throw new InvalidSubmissionException("A project code is required to confirm a project.");
        }
        if (sector == null) {
            throw new InvalidSubmissionException("A destination department is required to confirm a project.");
        }
        applyOutcomeDecision(decision, note, actor, time);
        this.projectCode = projectCode.trim();
        this.responsibleSectorId = sector.getId();
        this.responsibleSector = sector;
        this.forwardedAt = time;
        this.forwardedBy = clean(actor);
        this.confirmedAt = time;
        this.stage = SubmissionStage.CONFIRMED_AS_PROJECT;
        this.updatedAt = time;
    }

    public void requestOutcomeImprovement(String decision, String note, String actor, Instant time) {
        requireOutcomeOpen();
        applyOutcomeDecision(decision, note, actor, time);
        this.stage = SubmissionStage.IMPROVEMENT_REQUESTED;
        this.updatedAt = time;
    }

    public void closeWithOutcome(String decision, String note, String actor, Instant time) {
        requireOutcomeOpen();
        applyOutcomeDecision(decision, note, actor, time);
        this.stage = SubmissionStage.CLOSED;
        this.updatedAt = time;
    }

    private void requireOutcomeOpen() {
        if (outcomeStatus == OutcomeStatus.DECIDED
                || stage == SubmissionStage.DRAFT
                || stage == SubmissionStage.CLOSED
                || stage == SubmissionStage.COMPLETED
                || stage == SubmissionStage.CONFIRMED_AS_PROJECT) {
            throw new SubmissionConflictException("The outcome cannot be changed in the current case stage.");
        }
    }

    private void applyOutcomeDecision(String decision, String note, String actor, Instant time) {
        if (isBlank(decision)) {
            throw new InvalidSubmissionException("An outcome decision is required.");
        }
        if (isBlank(note)) {
            throw new InvalidSubmissionException("An outcome note is required.");
        }
        this.outcomeDecision = decision.trim();
        this.outcomeNote = note.trim();
        this.outcomeStatus = OutcomeStatus.DECIDED;
        this.outcomeDecidedBy = actor;
        this.outcomeDecidedAt = time;
        this.updatedAt = time;
    }

    public void touch(Instant time) {
        if (stage == SubmissionStage.COMPLETED || stage == SubmissionStage.CLOSED) {
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

    public Instant getPreferredAt() {
        return preferredAt;
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

    public String getResponsibleSectorWhatsappGroupUrl() {
        return responsibleSector == null ? null : responsibleSector.getWhatsappGroupUrl();
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

    /**
     * The intake form requires a title, purpose and problem statement before a
     * draft can be submitted. Drafts may be saved without them.
     */
    private void requireBriefContent() {
        if (isBlank(title) || isBlank(purpose) || isBlank(problemAddressed)) {
            throw new InvalidSubmissionException(
                    "Add the title, purpose and problem addressed before submitting the brief."
            );
        }
    }

    private void requireServiceRequestDetails() {
        if (category == SubmissionCategory.SERVICE_REQUEST
                && (isBlank(serviceRequested) || isBlank(expectedResult) || preferredAt == null)) {
            throw new InvalidSubmissionException(
                    "Service requests require the service requested, the expected result, and a preferred date and time."
            );
        }
    }

    private void requireAcceptedRules() {
        if (isBlank(acceptedRulesVersion)) {
            throw new InvalidSubmissionException(
                    "Accept the submission rules before submitting the brief."
            );
        }
    }

    /**
     * The intake form requires a way to reach the submitter before the brief
     * leaves DRAFT, but a draft may be saved without one.
     */
    private void requireContactDetails() {
        boolean hasPhone = !isBlank(submitterPhone);
        boolean hasEmail = !isBlank(submitterEmail) && submitterEmail.contains("@");
        if (!hasPhone && !hasEmail) {
            throw new InvalidSubmissionException(
                    "A phone number or email address is required before submitting the brief."
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
