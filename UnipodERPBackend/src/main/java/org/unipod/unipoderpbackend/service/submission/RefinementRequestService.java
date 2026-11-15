package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.RefinementRequestResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;

@Service
public class RefinementRequestService {
    private static final ZoneId CASE_CODE_ZONE = ZoneId.of("Africa/Kampala");

    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository requestedChanges;
    private final CaseRevisionRepository revisions;
    private final Clock clock;

    public RefinementRequestService(
            RefinementRequestRepository requests,
            RefinementRequestedChangeRepository requestedChanges,
            CaseRevisionRepository revisions,
            Clock clock
    ) {
        this.requests = requests;
        this.requestedChanges = requestedChanges;
        this.revisions = revisions;
        this.clock = clock;
    }

    @Transactional
    public RefinementRequestResponse ask(
            Submission submission,
            RefinementSource source,
            String guidanceNote,
            LocalDate dueDate,
            List<String> requestedItems,
            String requestedBy,
            Instant requestedAt
    ) {
        if (submission.getStage() != SubmissionStage.IMPROVEMENT_REQUESTED) {
            throw new SubmissionConflictException("The case must be in improvement requested before creating a request.");
        }
        String note = clean(guidanceNote);
        if (note == null || note.length() > 2000) {
            throw new InvalidSubmissionException("Refinement guidance must contain 1 to 2000 characters.");
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), CASE_CODE_ZONE);
        if (dueDate == null || dueDate.isBefore(today)) {
            throw new InvalidSubmissionException("A refinement due date on or after today is required.");
        }
        List<String> items = normalizeItems(requestedItems);
        if (items.isEmpty()) {
            throw new InvalidSubmissionException("At least one requested change is required.");
        }
        if (requests.existsByBriefIdAndStatus(submission.getId(), RefinementStatus.OPEN)) {
            throw new SubmissionConflictException("An open refinement request already exists for this case.");
        }

        int cycle = Math.toIntExact(requests.countByBriefId(submission.getId()) + 1);
        RefinementRequest request = requests.saveAndFlush(new RefinementRequest(
                submission.getId(),
                cycle,
                source,
                note,
                dueDate,
                requestedBy,
                requestedAt
        ));
        requestedChanges.saveAll(java.util.stream.IntStream.range(0, items.size())
                .mapToObj(index -> new RefinementRequestedChange(request.getId(), index + 1, items.get(index)))
                .toList());
        return response(request);
    }

    @Transactional(readOnly = true)
    public RefinementRequest openRequest(UUID briefId) {
        return findOpenRequest(briefId)
                .orElseThrow(() -> new SubmissionConflictException(
                        "No open refinement request exists for this case."
                ));
    }

    @Transactional(readOnly = true)
    public Optional<RefinementRequest> findOpenRequest(UUID briefId) {
        return requests.findTopByBriefIdAndStatusOrderByCycleDesc(briefId, RefinementStatus.OPEN);
    }

    @Transactional
    public void recordResponse(
            Submission submission,
            RefinementRequest request,
            String actor,
            String whatChanged,
            Instant submittedAt
    ) {
        String changeSummary = clean(whatChanged);
        if (changeSummary == null || changeSummary.length() > 2000) {
            throw new InvalidSubmissionException("Describe what changed in 1 to 2000 characters.");
        }
        if (request.getStatus() != RefinementStatus.OPEN
                || !request.getBriefId().equals(submission.getId())) {
            throw new SubmissionConflictException("The refinement request is no longer open for this case.");
        }
        request.respond(submittedAt);
        requests.save(request);
        revisions.save(new CaseRevision(
                submission.getId(),
                submission.getBriefVersion(),
                request.getId(),
                changeSummary,
                actor,
                submittedAt
        ));
    }

    private RefinementRequestResponse response(RefinementRequest request) {
        List<String> items = requestedChanges.findByRequestIdOrderByPositionAsc(request.getId()).stream()
                .map(RefinementRequestedChange::getText)
                .toList();
        return RefinementRequestResponse.from(request, items);
    }

    private static List<String> normalizeItems(List<String> items) {
        if (items == null || items.isEmpty() || items.size() > 50) {
            throw new InvalidSubmissionException("Provide between 1 and 50 requested changes.");
        }
        return items.stream()
                .map(item -> {
                    String normalized = clean(item);
                    if (normalized == null || normalized.length() > 500) {
                        throw new InvalidSubmissionException(
                                "Each requested change must contain 1 to 500 characters."
                        );
                    }
                    return normalized;
                })
                .toList();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
