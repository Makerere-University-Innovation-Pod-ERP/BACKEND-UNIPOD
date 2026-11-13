package org.unipod.unipoderpbackend.service.review;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.review.AssignReviewerRequest;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.dto.review.SaveReviewSettingsRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * Coordinator write operations for the review module: review settings and reviewer assignments.
 * Every operation locks the brief's submission row first, which serialises concurrent writes
 * for the same brief; the unique constraints are the safety net behind that lock.
 */
@Service
public class ReviewCommandService {
    static final String REVIEW_WRITE_ROLES = "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    static final int MAX_REVIEWS_NEEDED = 20;
    static final int MAX_ASSIGNMENTS_PER_ROUND = 20;
    static final int MAX_DEPARTMENT_LENGTH = 100;
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    static final String STAGE_REQUIRED =
            "Review settings and reviewers can only be changed while the brief is under review.";
    static final String SETTINGS_REQUIRED = "Set the review mode and number of reviews first.";
    static final String SETTINGS_CHANGED =
            "The review settings were changed by another user. Reload them before saving.";
    static final String ALREADY_ASSIGNED = "This reviewer is already assigned to this brief.";
    static final String ASSIGNMENTS_CHANGED =
            "The reviewers for this brief were changed by another user. Reload them and try again.";
    static final String NOT_ASSIGNED = "That reviewer is not assigned to this brief.";

    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
            "uq_review_settings_brief_round", SETTINGS_CHANGED,
            "uq_review_assignments_brief_round_reviewer", ALREADY_ASSIGNED,
            "uq_review_assignments_brief_round_position", ASSIGNMENTS_CHANGED
    );

    private final SubmissionRepository submissions;
    private final ReviewSettingsRepository settings;
    private final ReviewAssignmentRepository assignments;
    private final ApiUserRepository users;
    private final ReviewPanelQueryService panels;
    private final Clock clock;
    private final ReviewAudit audit;
    private final CaseStageHistoryRepository stageHistory;

    @Autowired
    public ReviewCommandService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ApiUserRepository users,
            ReviewPanelQueryService panels,
            Clock clock,
            ReviewAudit audit,
            CaseStageHistoryRepository stageHistory
    ) {
        this.submissions = submissions;
        this.settings = settings;
        this.assignments = assignments;
        this.users = users;
        this.panels = panels;
        this.clock = clock;
        this.audit = audit;
        this.stageHistory = stageHistory;
    }

    /** For unit tests of the rejection and conversion paths only; successful writes need the audit. */
    ReviewCommandService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ApiUserRepository users,
            ReviewPanelQueryService panels,
            Clock clock,
            CaseStageHistoryRepository stageHistory
    ) {
        this(submissions, settings, assignments, users, panels, clock, null, stageHistory);
    }

    @Transactional
    @PreAuthorize(REVIEW_WRITE_ROLES)
    public ReviewersPanelResponse saveSettings(
            UUID briefId,
            SaveReviewSettingsRequest request,
            Authentication authentication
    ) {
        lockBriefUnderReview(briefId, authentication);
        requireValidSettings(request);

        Optional<ReviewSettings> existing = settings.findTopByBriefIdOrderByRoundDesc(briefId);
        Map<String, Object> before = existing.map(ReviewAudit::settingsDetails).orElse(null);
        ReviewSettings saved = existing
                .map(current -> updatedSettings(current, request, authentication.getName()))
                .orElseGet(() -> newRoundOne(briefId, request, authentication.getName()));
        persist(() -> settings.saveAndFlush(saved), SETTINGS_CHANGED);
        audit.settingsSaved(authentication.getName(), briefId, before, saved);
        return panels.panelFor(briefId);
    }

    @Transactional
    @PreAuthorize(REVIEW_WRITE_ROLES)
    public ReviewersPanelResponse assignReviewer(
            UUID briefId,
            AssignReviewerRequest request,
            Authentication authentication
    ) {
        lockBriefUnderReview(briefId, authentication);
        String requestedReviewer = request.reviewerId() == null ? "" : request.reviewerId().trim();
        if (requestedReviewer.isEmpty()) {
            throw unprocessable("reviewerId is required.");
        }
        String department = blankToNull(request.department());
        if (department != null && department.length() > MAX_DEPARTMENT_LENGTH) {
            throw unprocessable("department must be at most %d characters.".formatted(MAX_DEPARTMENT_LENGTH));
        }

        ReviewSettings latest = settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .orElseThrow(() -> new SubmissionConflictException(SETTINGS_REQUIRED));
        ApiUser reviewer = users.findByEmailIgnoreCase(requestedReviewer)
                .filter(ApiUser::isEnabled)
                .filter(user -> user.getRole() == ApiUserRole.REVIEWER)
                .orElseThrow(() -> unprocessable("reviewerId must be the email of an active reviewer account."));

        List<ReviewAssignment> current = assignments.findByBriefIdAndRoundOrderByPositionAsc(
                briefId, latest.getRound());
        if (current.stream().anyMatch(assignment -> assignment.getReviewerId().equalsIgnoreCase(reviewer.getEmail()))) {
            throw new SubmissionConflictException(ALREADY_ASSIGNED);
        }
        if (current.size() >= MAX_ASSIGNMENTS_PER_ROUND) {
            throw new SubmissionConflictException(
                    "A brief can have at most %d reviewers in a review round.".formatted(MAX_ASSIGNMENTS_PER_ROUND));
        }
        int position = current.stream().mapToInt(ReviewAssignment::getPosition).max().orElse(0) + 1;

        ReviewAssignment assignment = new ReviewAssignment(
                briefId, latest.getRound(), reviewer.getEmail(), department, position, authentication.getName());
        persist(() -> assignments.saveAndFlush(assignment), ASSIGNMENTS_CHANGED);
        audit.reviewerAssigned(authentication.getName(), assignment);
        return panels.panelFor(briefId);
    }

    @Transactional
    @PreAuthorize(REVIEW_WRITE_ROLES)
    public ReviewersPanelResponse removeReviewer(
            UUID briefId,
            String reviewerId,
            Authentication authentication
    ) {
        lockBriefUnderReview(briefId, authentication);
        String requestedReviewer = reviewerId == null ? "" : reviewerId.trim();
        ReviewAssignment assignment = settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .flatMap(latest -> assignments.findByBriefIdAndRoundOrderByPositionAsc(briefId, latest.getRound())
                        .stream()
                        .filter(candidate -> candidate.getReviewerId().equalsIgnoreCase(requestedReviewer))
                        .findFirst())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_ASSIGNED));
        if (assignment.getStatus() == ReviewAssignmentStatus.SUBMITTED) {
            throw new SubmissionConflictException("A reviewer who has submitted a review cannot be removed.");
        }
        persist(() -> {
            assignments.delete(assignment);
            assignments.flush();
            return null;
        }, ASSIGNMENTS_CHANGED);
        audit.reviewerRemoved(authentication.getName(), assignment);
        return panels.panelFor(briefId);
    }

    private ReviewSettings newRoundOne(UUID briefId, SaveReviewSettingsRequest request, String caller) {
        if (request.version() != null) {
            throw unprocessable("version must not be supplied when creating review settings.");
        }
        return new ReviewSettings(
                briefId, 1, request.mode(), request.reviewsNeeded(), request.dueDate(), caller);
    }

    private ReviewSettings updatedSettings(ReviewSettings existing, SaveReviewSettingsRequest request, String caller) {
        if (request.version() == null) {
            throw unprocessable("version is required when updating review settings.");
        }
        if (!Objects.equals(existing.getVersion(), request.version())) {
            throw new SubmissionConflictException(SETTINGS_CHANGED);
        }
        long submitted = assignments.countByBriefIdAndRoundAndStatus(
                existing.getBriefId(), existing.getRound(), ReviewAssignmentStatus.SUBMITTED);
        if (submitted > 0 && request.mode() != existing.getMode()) {
            throw new SubmissionConflictException(
                    "The review mode cannot be changed after a review has been submitted.");
        }
        if (request.reviewsNeeded() < submitted) {
            throw new SubmissionConflictException(
                    "reviewsNeeded cannot be lower than the %d review(s) already submitted.".formatted(submitted));
        }
        existing.setMode(request.mode());
        existing.setReviewsNeeded(request.reviewsNeeded());
        existing.setDueDate(request.dueDate());
        existing.setSetBy(caller);
        return existing;
    }

    private void requireValidSettings(SaveReviewSettingsRequest request) {
        if (request.mode() == null) {
            throw unprocessable("mode is required.");
        }
        if (request.reviewsNeeded() == null) {
            throw unprocessable("reviewsNeeded is required.");
        }
        if (request.reviewsNeeded() < 1) {
            throw unprocessable("reviewsNeeded must be at least 1.");
        }
        if (request.reviewsNeeded() > MAX_REVIEWS_NEEDED) {
            throw unprocessable("reviewsNeeded must be at most %d.".formatted(MAX_REVIEWS_NEEDED));
        }
        LocalDate today = LocalDate.now(clock.withZone(HUB_ZONE));
        if (request.dueDate() != null && request.dueDate().isBefore(today)) {
            throw unprocessable("dueDate must not be in the past.");
        }
    }

    private Submission lockBriefUnderReview(UUID briefId, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        Submission brief = submissions.findVisibleByIdForUpdate(briefId, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        if (brief.getStage() == SubmissionStage.READY_FOR_REVIEW) {
            Instant now = clock.instant();
            brief.transitionTo(SubmissionStage.IN_REVIEW, now);
            stageHistory.save(new CaseStageHistory(
                    UUID.randomUUID(),
                    briefId,
                    SubmissionStage.READY_FOR_REVIEW,
                    SubmissionStage.IN_REVIEW,
                    authentication.getName(),
                    null,
                    now
            ));
        }
        if (brief.getStage() != SubmissionStage.IN_REVIEW) {
            throw new SubmissionConflictException(STAGE_REQUIRED);
        }
        return brief;
    }

    static <T> T persist(Supplier<T> write, String changedMessage) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(changedMessage);
        } catch (DataIntegrityViolationException exception) {
            String message = reviewConstraintMessage(exception);
            if (message == null) {
                throw exception;
            }
            throw new SubmissionConflictException(message);
        }
    }

    private static String reviewConstraintMessage(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : CONSTRAINT_MESSAGES.entrySet()) {
                if (message.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
