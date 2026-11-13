package org.unipod.unipoderpbackend.service.review;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.review.AddFollowUpNoteRequest;
import org.unipod.unipoderpbackend.dto.review.FollowUpNoteResponse;
import org.unipod.unipoderpbackend.dto.review.MyReviewResponse;
import org.unipod.unipoderpbackend.dto.review.ReviewSubmissionResponse;
import org.unipod.unipoderpbackend.dto.review.SubmitReviewRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Reviewer operations: submitting the caller's own review and reading the caller's own assignment.
 *
 * <p>Access is decided by the review module's own tables, not by the submission visibility rule:
 * a reviewer can act on a brief only when assigned to it in the latest review round. A reviewer who
 * is not assigned gets exactly the same 404 as for a brief that does not exist.
 *
 * <p>Submitting locks the brief's submission row first, which serialises concurrent submissions
 * for the same brief; the {@code uq_reviews_assignment} constraint and the assignment's optimistic
 * version are the safety net behind that lock. A submitted review is immutable: there is no update
 * or delete path.
 */
@Service
public class ReviewSubmissionService {
    static final String REVIEWER_ONLY = "hasRole('REVIEWER')";
    static final int MAX_COMMENT_LENGTH = 2000;

    static final String STAGE_REQUIRED = "Reviews can only be submitted while the brief is under review.";
    static final String ALREADY_SUBMITTED = "You have already submitted your review for this brief.";
    static final String ASSIGNMENT_CHANGED =
            "Your review assignment was changed by another request. Reload it and try again.";
    static final String NOT_YOUR_TURN = "It is not your turn yet. A reviewer before you has not submitted.";

    static final int MAX_NOTE_LENGTH = 1000;
    static final int MAX_FOLLOW_UP_NOTES = 20;
    static final String FOLLOW_UP_STAGE_REQUIRED =
            "Follow-up notes can only be added while the brief is under review.";
    static final String SUBMIT_BEFORE_FOLLOW_UP = "Submit your review before adding a follow-up note.";
    static final String FOLLOW_UP_LIMIT = "The follow-up note limit has been reached.";

    private static final String REVIEW_ASSIGNMENT_CONSTRAINT = "uq_reviews_assignment";

    private final SubmissionRepository submissions;
    private final ReviewSettingsRepository settings;
    private final ReviewAssignmentRepository assignments;
    private final ReviewRepository reviews;
    private final ReviewQuorumService quorumService;
    private final Clock clock;
    private final ReviewAudit audit;
    private final ReviewFollowUpNoteRepository notes;
    private final EntityManager entityManager;

    @Autowired
    public ReviewSubmissionService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ReviewRepository reviews,
            ReviewQuorumService quorumService,
            Clock clock,
            ReviewAudit audit,
            ReviewFollowUpNoteRepository notes,
            EntityManager entityManager
    ) {
        this.submissions = submissions;
        this.settings = settings;
        this.assignments = assignments;
        this.reviews = reviews;
        this.quorumService = quorumService;
        this.clock = clock;
        this.audit = audit;
        this.notes = notes;
        this.entityManager = entityManager;
    }

    /** For unit tests of the rejection and conversion paths only; successful writes need the audit. */
    ReviewSubmissionService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ReviewRepository reviews,
            ReviewQuorumService quorumService,
            Clock clock
    ) {
        this(submissions, settings, assignments, reviews, quorumService, clock, null, null, null);
    }

    @Transactional
    @PreAuthorize(REVIEWER_ONLY)
    public ReviewSubmissionResponse submit(UUID briefId, SubmitReviewRequest request, Authentication authentication) {
        requireReviewer(authentication);
        Submission brief = submissions.findByIdForUpdate(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        CallerAssignment caller = callerAssignment(briefId, authentication);
        ReviewAssignment assignment = caller.assignment();

        if (brief.getStage() != SubmissionStage.IN_REVIEW) {
            throw new SubmissionConflictException(STAGE_REQUIRED);
        }
        if (assignment.getStatus() == ReviewAssignmentStatus.SUBMITTED) {
            throw new SubmissionConflictException(ALREADY_SUBMITTED);
        }
        if (request.assignmentVersion() == null) {
            throw unprocessable("assignmentVersion is required.");
        }
        if (!Objects.equals(assignment.getVersion(), request.assignmentVersion())) {
            throw new SubmissionConflictException(ASSIGNMENT_CHANGED);
        }
        if (caller.settings().getMode() == ReviewMode.SEQUENTIAL && caller.round().stream()
                .anyMatch(other -> other.getPosition() < assignment.getPosition()
                        && other.getStatus() != ReviewAssignmentStatus.SUBMITTED)) {
            throw new SubmissionConflictException(NOT_YOUR_TURN);
        }
        if (request.decision() == null) {
            throw unprocessable("decision is required.");
        }
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (comment.isEmpty()) {
            throw unprocessable("comment is required and must not be blank.");
        }
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw unprocessable("comment must be at most %d characters.".formatted(MAX_COMMENT_LENGTH));
        }

        // PostgreSQL stores microseconds; truncate so the response matches what a later read returns.
        Review review = new Review(assignment.getId(), request.decision(), comment,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        Review saved = persist(() -> {
            Review stored = reviews.saveAndFlush(review);
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
            assignments.saveAndFlush(assignment);
            return stored;
        });
        // Round and position only: no decision, comment or reviewer email (see ReviewAudit).
        audit.reviewSubmitted(authentication.getName(), assignment);

        ReviewQuorum quorum = quorumService.quorumFor(caller.settings());
        return new ReviewSubmissionResponse(
                briefId,
                assignment.getRound(),
                assignment.getReviewerId(),
                saved.getDecision(),
                saved.getComment(),
                saved.getSubmittedAt(),
                assignment.getPosition(),
                new ReviewSubmissionResponse.Quorum(quorum.required(), quorum.completed(), quorum.met())
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize(REVIEWER_ONLY)
    public MyReviewResponse myReview(UUID briefId, Authentication authentication) {
        requireReviewer(authentication);
        if (!submissions.existsById(briefId)) {
            throw new SubmissionNotFoundException(briefId);
        }
        CallerAssignment caller = callerAssignment(briefId, authentication);
        ReviewAssignment assignment = caller.assignment();
        Review review = assignment.getStatus() == ReviewAssignmentStatus.SUBMITTED
                ? reviews.findByAssignmentId(assignment.getId()).orElse(null)
                : null;
        return new MyReviewResponse(
                briefId,
                assignment.getRound(),
                assignment.getPosition(),
                assignment.getDepartment(),
                assignment.getStatus(),
                assignment.getVersion(),
                caller.settings().getMode(),
                caller.settings().getDueDate(),
                review == null ? null : review.getDecision(),
                review == null ? null : review.getComment(),
                review == null ? null : review.getSubmittedAt()
        );
    }

    /**
     * Adds a follow-up note to the caller's own submitted review. The review itself is never modified.
     * The brief row lock serialises concurrent notes, so the per-review limit cannot be exceeded.
     */
    @Transactional
    @PreAuthorize(REVIEWER_ONLY)
    public FollowUpNoteResponse addFollowUpNote(
            UUID briefId,
            AddFollowUpNoteRequest request,
            Authentication authentication
    ) {
        requireReviewer(authentication);
        Submission brief = submissions.findByIdForUpdate(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        ReviewAssignment assignment = callerAssignment(briefId, authentication).assignment();

        if (brief.getStage() != SubmissionStage.IN_REVIEW) {
            throw new SubmissionConflictException(FOLLOW_UP_STAGE_REQUIRED);
        }
        Review review = assignment.getStatus() == ReviewAssignmentStatus.SUBMITTED
                ? reviews.findByAssignmentId(assignment.getId()).orElse(null)
                : null;
        if (review == null) {
            throw new SubmissionConflictException(SUBMIT_BEFORE_FOLLOW_UP);
        }
        String note = request.note() == null ? "" : request.note().trim();
        if (note.isEmpty()) {
            throw unprocessable("note is required and must not be blank.");
        }
        if (note.length() > MAX_NOTE_LENGTH) {
            throw unprocessable("note must be at most %d characters.".formatted(MAX_NOTE_LENGTH));
        }
        if (notes.countByReviewId(review.getId()) >= MAX_FOLLOW_UP_NOTES) {
            throw new SubmissionConflictException(FOLLOW_UP_LIMIT);
        }

        ReviewFollowUpNote saved = notes.saveAndFlush(
                new ReviewFollowUpNote(review.getId(), note, authentication.getName()));
        // Re-read the stored row so createdAt matches what PostgreSQL kept (microsecond precision).
        entityManager.refresh(saved);
        // Round and position only: no note text, decision or reviewer email (see ReviewAudit).
        audit.followUpAdded(authentication.getName(), assignment);
        return FollowUpNoteResponse.from(saved);
    }

    /** The caller's own follow-up notes in the latest round; empty until the review is submitted. */
    @Transactional(readOnly = true)
    @PreAuthorize(REVIEWER_ONLY)
    public List<FollowUpNoteResponse> followUpNotes(UUID briefId, Authentication authentication) {
        requireReviewer(authentication);
        if (!submissions.existsById(briefId)) {
            throw new SubmissionNotFoundException(briefId);
        }
        ReviewAssignment assignment = callerAssignment(briefId, authentication).assignment();
        if (assignment.getStatus() != ReviewAssignmentStatus.SUBMITTED) {
            return List.of();
        }
        return reviews.findByAssignmentId(assignment.getId())
                .map(review -> notes.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId()).stream()
                        .map(FollowUpNoteResponse::from)
                        .toList())
                .orElse(List.of());
    }

    /**
     * The caller's assignment in the latest review round. A brief without settings, or a caller
     * without an assignment in that round, is reported exactly like a brief that does not exist.
     */
    private CallerAssignment callerAssignment(UUID briefId, Authentication authentication) {
        String caller = authentication.getName();
        ReviewSettings latest = settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        List<ReviewAssignment> round = assignments.findByBriefIdAndRoundOrderByPositionAsc(
                briefId, latest.getRound());
        ReviewAssignment mine = round.stream()
                .filter(assignment -> assignment.getReviewerId().equalsIgnoreCase(caller))
                .findFirst()
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        return new CallerAssignment(latest, round, mine);
    }

    private static void requireReviewer(Authentication authentication) {
        boolean reviewer = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_REVIEWER".equals(authority.getAuthority()));
        if (!reviewer) {
            throw new AccessDeniedException("Only reviewers can submit or read their own review.");
        }
    }

    static <T> T persist(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(ALREADY_SUBMITTED);
        } catch (DataIntegrityViolationException exception) {
            if (!mentions(exception, REVIEW_ASSIGNMENT_CONSTRAINT)) {
                throw exception;
            }
            throw new SubmissionConflictException(ALREADY_SUBMITTED);
        }
    }

    private static boolean mentions(Throwable exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    private record CallerAssignment(ReviewSettings settings, List<ReviewAssignment> round, ReviewAssignment assignment) {
    }
}
