package org.unipod.unipoderpbackend.controller.improvement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * Builds an unsaved brief in a given stage the way the existing submission tests do: register, then the
 * model's own lifecycle transitions. Test setup only; production code never moves a stage itself.
 */
final class ImprovementTestBriefs {
    static final String SUBMITTER = "submitter@example.test";

    private static final Map<SubmissionStage, List<SubmissionStage>> PATHS = Map.ofEntries(
            Map.entry(SubmissionStage.DRAFT, List.of()),
            Map.entry(SubmissionStage.SUBMITTED, List.of()),
            Map.entry(SubmissionStage.FORWARDED, List.of(SubmissionStage.FORWARDED)),
            Map.entry(SubmissionStage.IN_FACT_FINDING, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING)),
            Map.entry(SubmissionStage.READY_FOR_REVIEW, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW)),
            Map.entry(SubmissionStage.IN_REVIEW, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW)),
            Map.entry(SubmissionStage.RESUBMITTED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.IMPROVEMENT_REQUESTED, SubmissionStage.RESUBMITTED)),
            Map.entry(SubmissionStage.CONFIRMED_AS_PROJECT, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT)),
            Map.entry(SubmissionStage.COMPLETED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT, SubmissionStage.IN_PROGRESS, SubmissionStage.COMPLETED)),
            Map.entry(SubmissionStage.CLOSED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CLOSED))
    );

    private ImprovementTestBriefs() {
    }

    static Submission inStage(SubmissionStage target, String codePrefix) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Improvement brief",
                "Purpose",
                "Problem",
                "receptionist@example.test",
                SUBMITTER,
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        if (target == SubmissionStage.DRAFT) {
            return brief;
        }
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register(codePrefix + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        List<SubmissionStage> path = PATHS.get(target);
        if (path == null) {
            throw new IllegalArgumentException("No test path to " + target);
        }
        for (SubmissionStage stage : path) {
            brief.transitionTo(stage, time);
        }
        return brief;
    }
}
