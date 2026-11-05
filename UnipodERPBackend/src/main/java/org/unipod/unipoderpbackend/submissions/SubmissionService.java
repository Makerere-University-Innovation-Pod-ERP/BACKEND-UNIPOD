package org.unipod.unipoderpbackend.submissions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class SubmissionService {
    private static final ZoneId CASE_CODE_ZONE = ZoneId.of("Africa/Kampala");

    private final SubmissionRepository submissions;
    private final CaseStageHistoryRepository stageHistory;
    private final CaseCodeGenerator caseCodeGenerator;
    private final Clock clock;

    @Autowired
    public SubmissionService(
            SubmissionRepository submissions,
            CaseStageHistoryRepository stageHistory,
            CaseCodeGenerator caseCodeGenerator
    ) {
        this(submissions, stageHistory, caseCodeGenerator, Clock.system(CASE_CODE_ZONE));
    }

    SubmissionService(
            SubmissionRepository submissions,
            CaseStageHistoryRepository stageHistory,
            CaseCodeGenerator caseCodeGenerator,
            Clock clock
    ) {
        this.submissions = submissions;
        this.stageHistory = stageHistory;
        this.caseCodeGenerator = caseCodeGenerator;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResponse create(CreateSubmissionRequest request, String actor) {
        if (request.category() == SubmissionCategory.GENERAL && hasStudentDetails(request)) {
            throw new InvalidSubmissionException(
                    "Student-specific details may only be provided for student submissions."
            );
        }

        Instant now = clock.instant();
        Submission submission = new Submission(
                UUID.randomUUID(),
                request.category(),
                request.title().trim(),
                request.purpose().trim(),
                request.problemAddressed().trim(),
                actor,
                clean(request.studentNumber()),
                clean(request.programme()),
                clean(request.supervisor()),
                request.recommendationLetterStatus(),
                now
        );
        submissions.save(submission);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                null,
                SubmissionStage.DRAFT,
                actor,
                now
        ));
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse register(UUID id, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Instant receivedAt = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        int year = caseYear(receivedAt);
        String code = caseCodeGenerator.next(submission.getCategory(), year);
        submission.register(code, receivedAt, actor);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.RECEIVED,
                actor,
                receivedAt
        ));
        return SubmissionResponse.from(submission);
    }

    private static boolean hasStudentDetails(CreateSubmissionRequest request) {
        return clean(request.studentNumber()) != null
                || clean(request.programme()) != null
                || clean(request.supervisor()) != null
                || request.recommendationLetterStatus() != null;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static int caseYear(Instant receivedAt) {
        return receivedAt.atZone(CASE_CODE_ZONE).getYear();
    }
}
