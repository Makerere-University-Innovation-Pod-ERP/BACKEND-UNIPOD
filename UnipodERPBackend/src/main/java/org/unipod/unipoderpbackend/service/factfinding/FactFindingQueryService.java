package org.unipod.unipoderpbackend.service.factfinding;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

@Service
public class FactFindingQueryService {
    private final SubmissionRepository submissions;
    private final FactFindingRepository checks;

    public FactFindingQueryService(SubmissionRepository submissions, FactFindingRepository checks) {
        this.submissions = submissions;
        this.checks = checks;
    }

    @Transactional(readOnly = true)
    public FactFindingResponse getForBrief(UUID briefId, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        Submission brief = submissions.findVisibleById(briefId, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        return checks.findTopByBriefIdOrderByRoundDesc(briefId)
                .map(check -> FactFindingResponse.from(brief, check))
                .orElseGet(() -> FactFindingResponse.notStarted(brief));
    }
}
