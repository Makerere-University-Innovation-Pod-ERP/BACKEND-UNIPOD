package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.reception.ReceptionQueueResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseStageHistoryResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterSubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

@Service
public class SubmissionService {
    private static final ZoneId CASE_CODE_ZONE = ZoneId.of("Africa/Kampala");

    private final SubmissionRepository submissions;
    private final CaseStageHistoryRepository stageHistory;
    private final CaseCodeGenerator caseCodeGenerator;
    private final SubmissionDocumentRepository documents;
    private final NotificationOutbox notificationOutbox;
    private final PipelineMetricsRepository metrics;
    private final SectorService sectorsCatalog;
    private final Clock clock;

    @Autowired
    public SubmissionService(
            SubmissionRepository submissions,
            CaseStageHistoryRepository stageHistory,
            CaseCodeGenerator caseCodeGenerator,
            SubmissionDocumentRepository documents,
            NotificationOutbox notificationOutbox,
            PipelineMetricsRepository metrics,
            SectorService sectorsCatalog,
            Clock clock
    ) {
        this.submissions = submissions;
        this.stageHistory = stageHistory;
        this.caseCodeGenerator = caseCodeGenerator;
        this.documents = documents;
        this.notificationOutbox = notificationOutbox;
        this.metrics = metrics;
        this.sectorsCatalog = sectorsCatalog;
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
                request.submitterId() == null || request.submitterId().isBlank()
                        ? actor
                        : request.submitterId().trim(),
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
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse forward(UUID id, UUID sectorId, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Sector sector = sectorsCatalog.requireActive(sectorId);
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        submission.forward(sector, now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.FORWARDED,
                actor,
                now
        ));
        notificationOutbox.recordForwarded(new NotificationOutbox.ForwardedNotification(
                submission.getId(),
                sectorId,
                sector.getName(),
                sector.getNotificationEmail(),
                submission.getTitle(),
                submission.getCode(),
                actor,
                now
        ));
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse advance(UUID id, SubmissionStage targetStage, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        submission.advanceTo(targetStage, now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                targetStage,
                actor,
                now
        ));
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public DocumentResponse addDocument(UUID id, CreateDocumentRequest request, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        String fileReference = clean(request.fileReference());
        if (fileReference != null && !fileReference.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new InvalidSubmissionException("The file reference is invalid.");
        }
        Instant now = clock.instant();
        submission.touch(now);
        SubmissionDocument document = documents.save(new SubmissionDocument(
                UUID.randomUUID(),
                id,
                request.name().trim(),
                request.status(),
                fileReference,
                actor,
                now
        ));
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> all(Pageable pageable) {
        return submissions.findAllByOrderByUpdatedAtDescIdAsc(pageable).map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> search(
            SubmissionCategory category,
            SubmissionStage stage,
            UUID sectorId,
            String query,
            Pageable pageable,
            org.springframework.security.core.Authentication authentication
    ) {
        String scope = SubmissionVisibility.scope(authentication);
        return submissions.searchVisible(
                category,
                stage,
                sectorId,
                query == null ? null : escapeLike(clean(query)),
                scope,
                authentication.getName(),
                pageable
        ).map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> searchByCodeOrTitle(
            String query,
            Pageable pageable,
            org.springframework.security.core.Authentication authentication
    ) {
        if (query == null || query.isBlank()) {
            throw new InvalidSubmissionException("The search code is required.");
        }
        String scope = SubmissionVisibility.scope(authentication);
        String principal = authentication.getName();
        Submission exact = submissions.findVisibleByCode(query.trim(), scope, principal).orElse(null);
        if (exact != null) {
            if (pageable.getOffset() > 0) {
                return new org.springframework.data.domain.PageImpl<>(
                        List.of(), pageable, 1
                );
            }
            return new org.springframework.data.domain.PageImpl<>(
                    List.of(SubmissionResponse.from(exact)),
                    pageable,
                    1
            );
        }
        return submissions.searchVisible(
                null, null, null, escapeLike(query.trim()), scope, principal, pageable
        ).map(SubmissionResponse::from);
    }

    @Transactional
    public SubmissionResponse assignReviewer(UUID id, String subject) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        submission.assignReviewer(subject, clock.instant());
        return SubmissionResponse.from(submission);
    }

    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> mine(String actor, Pageable pageable) {
        return submissions.findAllByRegisteredByOrderByUpdatedAtDescIdAsc(actor, pageable)
                .map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SubmitterSubmissionResponse> forSubmitter(String submitterId, Pageable pageable) {
        return submissions.findAllBySubmitterIdOrderByUpdatedAtDescIdAsc(submitterId, pageable)
                .map(SubmitterSubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public CaseFileResponse caseFile(UUID id) {
        Submission submission = submissions.findById(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        List<DocumentResponse> documentResponses = documents
                .findAllBySubmissionIdOrderByCreatedAtAscIdAsc(id)
                .stream()
                .map(DocumentResponse::from)
                .toList();
        List<CaseStageHistoryResponse> history = stageHistory
                .findAllBySubmissionIdOrderByChangedAtAsc(id)
                .stream()
                .map(CaseStageHistoryResponse::from)
                .toList();
        return new CaseFileResponse(SubmissionResponse.from(submission), documentResponses, history);
    }

    @Transactional(readOnly = true)
    public ReceptionQueueResponse receptionQueue(Pageable pageable) {
        Page<SubmissionResponse> page = submissions
                .findAllByStageOrderByUpdatedAtDescIdAsc(SubmissionStage.DRAFT, pageable)
                .map(SubmissionResponse::from);
        PipelineMetricsRepository.QueueCounts counts = metrics.receptionQueue(
                clock.instant().minus(Duration.ofDays(14))
        );
        return new ReceptionQueueResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                counts.inQueue(),
                counts.awaitingAction(),
                counts.recentlyUpdated()
        );
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview() {
        return metrics.overview(clock.instant().minus(Duration.ofDays(14)));
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
