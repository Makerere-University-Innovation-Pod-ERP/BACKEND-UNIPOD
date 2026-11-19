package org.unipod.unipoderpbackend.service.improvement;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.improvement.AcceptRevisionRequest;
import org.unipod.unipoderpbackend.dto.improvement.AcceptRevisionResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;

/**
 * The coordinator accepts the submitter's latest improved version and returns the brief to review. The stage
 * change goes through Waluube's {@link CaseFileService#transition} (RESUBMITTED to IN_REVIEW), which joins this
 * transaction: if the audit entry fails, the stage change, its history row and its audit entry roll back.
 *
 * <p>Known gap: the brief returns to its existing review round; no new round is started here.
 */
@Service
public class RevisionAcceptanceService {
    static final String ACCEPT_ROLES = "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    static final String ACCEPT_NOTE = "Improved version accepted; returned to review.";
    static final String NO_SUCH_REVISION = "That revision does not exist for this brief.";
    static final String NEWER_VERSION = "A newer version has been submitted.";
    static final String NOTHING_WAITING = "There is no improved version waiting for review.";

    private final SubmissionRepository submissions;
    private final CaseRevisionRepository revisions;
    private final RefinementRequestRepository requests;
    private final CaseFileService caseFiles;
    private final ImprovementAudit audit;
    private final Clock clock;

    public RevisionAcceptanceService(
            SubmissionRepository submissions,
            CaseRevisionRepository revisions,
            RefinementRequestRepository requests,
            CaseFileService caseFiles,
            ImprovementAudit audit,
            Clock clock
    ) {
        this.submissions = submissions;
        this.revisions = revisions;
        this.requests = requests;
        this.caseFiles = caseFiles;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(ACCEPT_ROLES)
    public AcceptRevisionResponse accept(
            UUID briefId,
            Long revisionId,
            AcceptRevisionRequest request,
            Authentication authentication
    ) {
        boolean operator = ImprovementRoles.has(authentication, "ROLE_INNOVATION_COORDINATOR")
                || ImprovementRoles.has(authentication, "ROLE_ADMINISTRATOR");
        if (!operator) {
            throw new AccessDeniedException("Only a coordinator or administrator can accept an improved version.");
        }
        Submission brief = submissions.findByIdForUpdate(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        CaseRevision revision = revisions.findById(revisionId)
                .filter(candidate -> candidate.getBriefId().equals(briefId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NO_SUCH_REVISION));
        CaseRevision latest = revisions.findTopByBriefIdOrderByVersionNumberDesc(briefId).orElseThrow();
        if (!latest.getId().equals(revision.getId())) {
            throw new SubmissionConflictException(NEWER_VERSION);
        }
        if (brief.getStage() != SubmissionStage.RESUBMITTED) {
            throw new SubmissionConflictException(NOTHING_WAITING);
        }
        if (request.caseVersion() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "caseVersion is required to return the brief to review.");
        }
        RefinementRequest answered = requests.findById(revision.getRequestId()).orElseThrow();

        // Waluube's transition: roles, version check, fact-finding guard, history row and audit entry.
        caseFiles.transition(briefId, new CaseTransitionRequest(CaseStage.IN_REVIEW, ACCEPT_NOTE, null, null,
                request.caseVersion()), authentication);

        // Structural values only (see ImprovementAudit).
        audit.improvedVersionAccepted(authentication.getName(), answered, revision.getVersionNumber());
        return new AcceptRevisionResponse(briefId, revision.getId(), revision.getVersionNumber(), brief.getStage(),
                authentication.getName(), clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
