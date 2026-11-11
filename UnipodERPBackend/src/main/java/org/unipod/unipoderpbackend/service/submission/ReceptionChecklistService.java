package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckItemRequest;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckItemResponse;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckResponse;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckUpdateRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheck;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheckItem;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.ReceptionCheckRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

@Service
public class ReceptionChecklistService {
    private final SubmissionRepository submissions;
    private final ReceptionCheckRepository checks;
    private final Clock clock;

    public ReceptionChecklistService(
            SubmissionRepository submissions,
            ReceptionCheckRepository checks,
            Clock clock
    ) {
        this.submissions = submissions;
        this.checks = checks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReceptionCheckResponse get(UUID submissionId) {
        Submission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        return response(submission, existingByKey(submissionId));
    }

    @Transactional
    public ReceptionCheckResponse update(
            UUID submissionId,
            ReceptionCheckUpdateRequest request,
            String actor
    ) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        requireCheckable(submission);

        Instant now = clock.instant();
        Map<ReceptionCheckItem, ReceptionCheck> existing = existingByKey(submissionId);
        for (ReceptionCheckItemRequest itemRequest : request.items()) {
            ReceptionCheckItem item = requireItem(itemRequest.key());
            requireApplicable(item, submission.getCategory());
            ReceptionCheck check = existing.get(item);
            if (check == null) {
                check = checks.save(new ReceptionCheck(
                        UUID.randomUUID(),
                        submissionId,
                        item.name(),
                        item.label(),
                        now
                ));
                existing.put(item, check);
            }
            check.update(
                    Boolean.TRUE.equals(itemRequest.checked()),
                    clean(itemRequest.note()),
                    actor,
                    now
            );
        }
        submission.touch(now);
        checks.flush();
        return response(submission, existing);
    }

    private static void requireCheckable(Submission submission) {
        if (submission.getStage() != SubmissionStage.DRAFT
                && submission.getStage() != SubmissionStage.RECEIVED) {
            throw new SubmissionConflictException(
                    "The reception checklist can only be changed before the submission is forwarded."
            );
        }
    }

    private static ReceptionCheckItem requireItem(String key) {
        try {
            return ReceptionCheckItem.fromKey(key.trim());
        } catch (IllegalArgumentException exception) {
            throw new InvalidSubmissionException("The reception checklist item is unknown.");
        }
    }

    private static void requireApplicable(ReceptionCheckItem item, SubmissionCategory category) {
        if (item.studentOnly() && category != SubmissionCategory.STUDENT) {
            throw new InvalidSubmissionException(
                    "The reception checklist item does not apply to this submission."
            );
        }
    }

    private Map<ReceptionCheckItem, ReceptionCheck> existingByKey(UUID submissionId) {
        Map<ReceptionCheckItem, ReceptionCheck> result = new EnumMap<>(ReceptionCheckItem.class);
        for (ReceptionCheck check : checks.findAllBySubmissionIdOrderByItemKeyAsc(submissionId)) {
            try {
                result.put(ReceptionCheckItem.fromKey(check.getItemKey()), check);
            } catch (IllegalArgumentException ignored) {
                // Retired checklist lines stay in storage but are not shown.
            }
        }
        return result;
    }

    private static ReceptionCheckResponse response(
            Submission submission,
            Map<ReceptionCheckItem, ReceptionCheck> existing
    ) {
        List<ReceptionCheckItemResponse> items = ReceptionCheckItem
                .applicableTo(submission.getCategory())
                .stream()
                .map(item -> response(item, existing.get(item)))
                .toList();
        int checkedCount = (int) items.stream().filter(ReceptionCheckItemResponse::checked).count();
        return new ReceptionCheckResponse(
                submission.getId(),
                checkedCount == items.size(),
                checkedCount,
                items.size(),
                items
        );
    }

    private static ReceptionCheckItemResponse response(
            ReceptionCheckItem item,
            ReceptionCheck check
    ) {
        if (check == null) {
            return new ReceptionCheckItemResponse(
                    item.name(),
                    item.label(),
                    false,
                    null,
                    null,
                    null,
                    null
            );
        }
        return ReceptionCheckItemResponse.from(check);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
