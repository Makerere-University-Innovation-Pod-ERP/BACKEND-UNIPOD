package org.unipod.unipoderpbackend.service.review;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.dto.review.StartReviewRoundRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Starts the next review round for a brief that came back from improvement. Earlier rounds are never changed
 * and nothing is copied: the new round starts with no reviewers. Every review operation follows the latest
 * round, so once this row exists, settings, assignments, quorum, panel, queue and submissions act on it.
 *
 * <p>"Came back from improvement" means: the brief is IN_REVIEW and its latest case revision was submitted after
 * the latest review round was created (case_revisions.submitted_at &gt; review_settings.created_at). The only way
 * from RESUBMITTED to IN_REVIEW is the transition that accepts that revision.
 */
@Service
public class ReviewRoundService {
    static final String OPERATOR_ROLES = "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    static final int MAX_REVIEWS_NEEDED = 20;
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    static final String STAGE_REQUIRED = "A new review round can only start while the brief is in review.";
    static final String NO_ROUND = "There is no review round to follow. Set the review settings first.";
    static final String SETTINGS_CHANGED = "The review settings were changed by another user. Reload and try again.";
    static final String STILL_OPEN = "The current review round is still open.";
    static final String NOT_IMPROVED = "A new review round can only start after an improved version has been accepted.";
    private static final String ROUND_CONSTRAINT = "uq_review_settings_brief_round";

    private final SubmissionRepository submissions;
    private final ReviewSettingsRepository settings;
    private final CaseRevisionRepository revisions;
    private final ReviewQuorumService quorumService;
    private final ReviewPanelQueryService panels;
    private final ReviewAudit audit;
    private final Clock clock;

    public ReviewRoundService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            CaseRevisionRepository revisions,
            ReviewQuorumService quorumService,
            ReviewPanelQueryService panels,
            ReviewAudit audit,
            Clock clock
    ) {
        this.submissions = submissions;
        this.settings = settings;
        this.revisions = revisions;
        this.quorumService = quorumService;
        this.panels = panels;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(OPERATOR_ROLES)
    public ReviewersPanelResponse start(UUID briefId, StartReviewRoundRequest request, Authentication authentication) {
        boolean operator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_INNOVATION_COORDINATOR".equals(authority.getAuthority())
                        || "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
        if (!operator) {
            throw new AccessDeniedException("Only a coordinator or administrator can start a review round.");
        }
        Submission brief = submissions.findByIdForUpdate(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        if (brief.getStage() != SubmissionStage.IN_REVIEW) {
            throw new SubmissionConflictException(STAGE_REQUIRED);
        }
        ReviewSettings latest = settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .orElseThrow(() -> new SubmissionConflictException(NO_ROUND));
        if (request.settingsVersion() == null) {
            throw unprocessable("settingsVersion is required.");
        }
        if (!Objects.equals(latest.getVersion(), request.settingsVersion())) {
            throw new SubmissionConflictException(SETTINGS_CHANGED);
        }
        if (!quorumService.quorumFor(latest).met()) {
            throw new SubmissionConflictException(STILL_OPEN);
        }
        boolean improvedSinceRound = revisions.findTopByBriefIdOrderByVersionNumberDesc(briefId)
                .filter(revision -> revision.getSubmittedAt().isAfter(latest.getCreatedAt()))
                .isPresent();
        if (!improvedSinceRound) {
            throw new SubmissionConflictException(NOT_IMPROVED);
        }
        if (request.mode() == null) {
            throw unprocessable("mode is required.");
        }
        if (request.reviewsNeeded() == null) {
            throw unprocessable("reviewsNeeded is required.");
        }
        if (request.reviewsNeeded() < 1 || request.reviewsNeeded() > MAX_REVIEWS_NEEDED) {
            throw unprocessable("reviewsNeeded must be between 1 and %d.".formatted(MAX_REVIEWS_NEEDED));
        }
        if (request.dueDate() != null && request.dueDate().isBefore(LocalDate.now(clock.withZone(HUB_ZONE)))) {
            throw unprocessable("dueDate must not be in the past.");
        }

        ReviewSettings next = persist(() -> settings.saveAndFlush(new ReviewSettings(briefId, latest.getRound() + 1,
                request.mode(), request.reviewsNeeded(), request.dueDate(), authentication.getName())));
        audit.roundStarted(authentication.getName(), next);
        return panels.panelFor(briefId);
    }

    static <T> T persist(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(SETTINGS_CHANGED);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(ROUND_CONSTRAINT)) {
                    throw new SubmissionConflictException(SETTINGS_CHANGED);
                }
            }
            throw exception;
        }
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
