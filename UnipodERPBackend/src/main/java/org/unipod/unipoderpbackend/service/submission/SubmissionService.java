package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.reception.ReceptionQueueResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseStageHistoryResponse;
import org.unipod.unipoderpbackend.dto.submission.ConfirmProjectRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.MissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.RejectSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.RequestInformationRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterSubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

@Service
public class SubmissionService {
    private static final ZoneId CASE_CODE_ZONE = ZoneId.of("Africa/Kampala");
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final SubmissionRepository submissions;
    private final CaseStageHistoryRepository stageHistory;
    private final CaseCodeGenerator caseCodeGenerator;
    private final SubmissionDocumentRepository documents;
    private final MissingItemRepository missingItems;
    private final NotificationOutbox notificationOutbox;
    private final PipelineMetricsRepository metrics;
    private final SectorService sectorsCatalog;
    private final ReceptionChecklistService receptionChecklists;
    private final String receptionEmail;
    private final Clock clock;

    @Autowired
    public SubmissionService(
            SubmissionRepository submissions,
            CaseStageHistoryRepository stageHistory,
            CaseCodeGenerator caseCodeGenerator,
            SubmissionDocumentRepository documents,
            MissingItemRepository missingItems,
            NotificationOutbox notificationOutbox,
            PipelineMetricsRepository metrics,
            SectorService sectorsCatalog,
            ReceptionChecklistService receptionChecklists,
            @Value("${notifications.reception.email:}")
            String receptionEmail,
            Clock clock
    ) {
        this.submissions = submissions;
        this.stageHistory = stageHistory;
        this.caseCodeGenerator = caseCodeGenerator;
        this.documents = documents;
        this.missingItems = missingItems;
        this.notificationOutbox = notificationOutbox;
        this.metrics = metrics;
        this.sectorsCatalog = sectorsCatalog;
        this.receptionChecklists = receptionChecklists;
        this.receptionEmail = receptionEmail;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResponse create(CreateSubmissionRequest request, String actor) {
        return create(request, actor, false);
    }

    @Transactional
    public SubmissionResponse create(CreateSubmissionRequest request, String actor, boolean submitter) {
        requireSubmitterRecommendationStatus(request, submitter);
        requireCategoryDetails(request);

        Instant now = clock.instant();
        String submitterId = submitter || request.submitterId() == null || request.submitterId().isBlank()
                ? actor
                : request.submitterId().trim();
        String submitterEmail = submitter
                ? actor
                : clean(request.submitterEmail()) == null
                        ? emailFromSubmitterId(request.submitterId())
                        : request.submitterEmail().trim();
        Submission submission = new Submission(
                UUID.randomUUID(),
                request.category(),
                request.title().trim(),
                request.purpose().trim(),
                request.problemAddressed().trim(),
                actor,
                submitterId,
                clean(request.studentNumber()),
                clean(request.programme()),
                clean(request.supervisor()),
                request.recommendationLetterStatus(),
                submitterEmail,
                submitter ? null : clean(request.submitterName()),
                now
        );
        applyIntakeDetails(submission, request);
        submission.assignTrackingReference(caseCodeGenerator.next(
                request.category(),
                caseYear(now)
        ));
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
    public SubmissionResponse updateDraft(UUID id, CreateSubmissionRequest request, String actor, boolean submitter) {
        requireSubmitterRecommendationStatus(request, submitter);
        requireCategoryDetails(request);
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        requireSubmitterOwnership(submission, actor, submitter);
        if (submission.getCategory() != request.category()) {
            throw new InvalidSubmissionException("The submission category cannot be changed after creation.");
        }
        submission.updateDraft(
                request.title().trim(),
                request.purpose().trim(),
                request.problemAddressed().trim(),
                clean(request.studentNumber()),
                clean(request.programme()),
                clean(request.supervisor()),
                request.recommendationLetterStatus(),
                clock.instant()
        );
        applyIntakeDetails(submission, request);
        if (submitter) {
            submission.clearInformationRequest();
        } else {
            if (request.submitterEmail() != null && !request.submitterEmail().isBlank()) {
                submission.updateSubmitterEmail(request.submitterEmail().trim());
            }
            if (request.submitterName() != null) {
                submission.updateSubmitterName(request.submitterName());
            }
        }
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    /**
     * Registers a draft on behalf of reception. Retained for the assisted and
     * legacy intake flow; submitter-created briefs use {@link #submit}.
     */
    @Transactional
    public SubmissionResponse register(UUID id, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Instant receivedAt = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        int year = caseYear(receivedAt);
        String code = submission.getCode() == null
                ? caseCodeGenerator.next(submission.getCategory(), year)
                : submission.getCode();
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

    /**
     * Moves a draft into the submitter-facing intake queue. This is the
     * submitter's own action and allocates the tracking reference when one is
     * still missing.
     */
    @Transactional
    public SubmissionResponse submit(UUID id, String actor, boolean submitter) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        requireSubmitterOwnership(submission, actor, submitter);
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        String code = submission.getCode() == null
                ? caseCodeGenerator.next(submission.getCategory(), caseYear(now))
                : submission.getCode();
        submission.submit(code, now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.SUBMITTED,
                actor,
                now
        ));
        notifySubmitter(
                submission,
                "submission.submitted",
                "Submission " + submission.getCode() + " received",
                "Your brief has been submitted for reception review.",
                actor,
                now
        );
        notifyReception(
                submission,
                "submission.submitted",
                "New brief " + submission.getCode() + " submitted",
                "A brief has been submitted and is waiting in the reception queue.",
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    /**
     * Records the submitter's answer to an information request and returns the
     * brief to the intake queue as a new brief version.
     */
    @Transactional
    public SubmissionResponse reply(UUID id, ReplySubmissionRequest request, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        requireSubmitterReplyOwnership(submission, actor);
        Instant now = clock.instant();
        submission.applyReplyDetails(
                clean(request.title()),
                clean(request.purpose()),
                clean(request.problemAddressed()),
                clean(request.studentNumber()),
                clean(request.programme()),
                clean(request.supervisor()),
                request.recommendationLetterStatus(),
                clean(request.submitterPhone()),
                clean(request.serviceRequested()),
                clean(request.department()),
                clean(request.expectedResult()),
                now
        );
        SubmissionStage previousStage = submission.getStage();
        submission.reply(now);
        String note = clean(request.note()) == null ? "Submitter replied with the requested information." : clean(request.note());
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.SUBMITTED,
                actor,
                note,
                now
        ));
        resolveOpenInformationItems(submission.getId(), actor, now);
        notifySubmitter(
                submission,
                "submission.reply_received",
                "Reply received for submission " + submission.getCode(),
                "Your reply has been received and the brief is back with reception.",
                actor,
                now
        );
        notifyReception(
                submission,
                "submission.reply_received",
                "Reply received for " + submission.getCode(),
                "The submitter replied with the requested information; the brief is back in the reception queue.",
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse confirmAsProject(UUID id, ConfirmProjectRequest request, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        if (submission.getStage() == SubmissionStage.SUBMITTED) {
            receptionChecklists.requireComplete(id);
        }
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        String projectCode = clean(request.projectCode());
        if (projectCode == null) {
            projectCode = caseCodeGenerator.nextProject(caseYear(now));
        }
        submission.confirmAsProject(projectCode, clean(request.note()), actor, now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.CONFIRMED_AS_PROJECT,
                actor,
                clean(request.note()),
                now
        ));
        notifySubmitter(
                submission,
                "submission.confirmed_as_project",
                "Submission " + submission.getCode() + " confirmed as a project",
                "Your brief has been confirmed as project " + projectCode + ".",
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse reject(UUID id, RejectSubmissionRequest request, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        submission.reject(request.reason(), now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.REJECTED,
                actor,
                clean(request.reason()),
                now
        ));
        notifySubmitter(
                submission,
                "submission.rejected",
                "Submission " + submission.getCode() + " not taken forward",
                "Your brief was not taken forward. Reason: " + request.reason().trim(),
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse forward(UUID id, UUID sectorId, String actor) {
        return forward(id, sectorId, null, actor);
    }

    @Transactional
    public SubmissionResponse forward(UUID id, UUID sectorId, String note, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Sector sector = sectorsCatalog.requireActive(sectorId);
        Instant now = clock.instant();
        if (submission.getStage() == SubmissionStage.SUBMITTED) {
            receptionChecklists.requireComplete(id);
        }
        if (submission.getStage() == SubmissionStage.DRAFT) {
            SubmissionStage draftStage = submission.getStage();
            String code = submission.getCode() == null
                    ? caseCodeGenerator.next(submission.getCategory(), caseYear(now))
                    : submission.getCode();
            submission.register(code, now, actor);
            stageHistory.save(new CaseStageHistory(
                    UUID.randomUUID(),
                    submission.getId(),
                    draftStage,
                    SubmissionStage.RECEIVED,
                    actor,
                    now
            ));
        }
        SubmissionStage previousStage = submission.getStage();
        submission.forward(sector, note, actor, now);
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                SubmissionStage.FORWARDED,
                actor,
                clean(note),
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
        notifySubmitter(
                submission,
                "submission.forwarded_copy",
                "Submission " + submission.getCode() + " forwarded",
                "Your brief has been forwarded to " + sector.getName() + " for assessment.",
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    @Transactional
    public SubmissionResponse requestInformation(UUID id, RequestInformationRequest request, String actor) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        Instant now = clock.instant();
        List<MissingItemRequest> requestedItems = request.items() == null
                ? List.of()
                : request.items();
        String note = clean(request.note());
        String reason = clean(request.reason());
        if (note == null && reason == null && requestedItems.isEmpty()) {
            throw new InvalidSubmissionException(
                    "A reason or at least one missing item is required when requesting more information."
            );
        }
        if (!isEmailAddress(submission.getSubmitterEmail())) {
            throw new InvalidSubmissionException(
                    "A submitter email address is required before requesting more information."
            );
        }
        String summary = note != null
                ? note
                : reason != null ? reason : summarize(requestedItems);
        SubmissionStage previousStage = submission.getStage();
        submission.requestInformation(summary, actor, now);
        SubmissionStage resultingStage = submission.getStage();
        stageHistory.save(new CaseStageHistory(
                UUID.randomUUID(),
                submission.getId(),
                previousStage,
                resultingStage,
                actor,
                summary,
                now
        ));
        for (MissingItemRequest item : requestedItems) {
            missingItems.save(createMissingItem(submission.getId(), item, actor, now));
        }
        notificationOutbox.recordInformationRequested(
                submission.getId(),
                submission.getCode(),
                submission.getSubmitterId(),
                submission.getSubmitterEmail(),
                summary,
                actor,
                now
        );
        submissions.flush();
        return SubmissionResponse.from(submission);
    }

    private static String summarize(List<MissingItemRequest> items) {
        String summary = items.stream()
                .map(MissingItemRequest::label)
                .filter(label -> label != null && !label.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining("; "));
        if (summary.isBlank()) {
            throw new InvalidSubmissionException("A label is required for every missing item.");
        }
        return summary.length() <= 2000 ? summary : summary.substring(0, 2000);
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
        return addDocument(id, request, actor, false);
    }

    @Transactional
    public DocumentResponse addDocument(UUID id, CreateDocumentRequest request, String actor, boolean submitter) {
        requireSubmitterDocumentStatus(request.status(), submitter);
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        requireSubmitterDocumentOwnership(submission, actor, submitter);
        String fileReference = clean(request.fileReference());
        if (fileReference != null && !fileReference.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new InvalidSubmissionException("The file reference is invalid.");
        }
        Instant now = clock.instant();
        submission.touch(now);
        DocumentType documentType = request.documentType() == null
                ? DocumentType.OTHER
                : request.documentType();
        long version = request.version() == null
                ? documents.maxVersion(id, documentType) + 1
                : request.version();
        SubmissionDocument document = new SubmissionDocument(
                UUID.randomUUID(),
                id,
                request.name().trim(),
                documentType,
                request.status(),
                fileReference,
                null,
                null,
                null,
                actor,
                now
        );
        document.assignVersion(version);
        documents.save(document);
        resolveOpenDocumentItems(id, documentType, document.getName(), actor, now);
        documents.flush();
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
            LocalDate receivedFrom,
            LocalDate receivedTo,
            Pageable pageable,
            org.springframework.security.core.Authentication authentication
    ) {
        validateDateRange(receivedFrom, receivedTo);
        String scope = SubmissionVisibility.scope(authentication);
        boolean dateFiltered = receivedFrom != null || receivedTo != null;
        return submissions.searchVisible(
                category,
                stage,
                sectorId,
                escapeLike(clean(query)),
                dateFiltered,
                receivedFrom == null ? Instant.EPOCH : startOfDay(receivedFrom),
                receivedTo == null ? FAR_FUTURE : startOfDayExclusive(receivedTo),
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
        String term = query.trim();
        Submission exact = submissions.findVisibleByCode(term.toUpperCase(Locale.ROOT), scope, principal)
                .orElse(null);
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
                null, null, null, escapeLike(term), false, Instant.EPOCH, FAR_FUTURE,
                scope, principal, pageable
        ).map(SubmissionResponse::from);
    }

    private static void validateDateRange(LocalDate receivedFrom, LocalDate receivedTo) {
        if (receivedFrom != null && receivedTo != null && receivedFrom.isAfter(receivedTo)) {
            throw new InvalidSubmissionException("The received date range is invalid.");
        }
    }

    private static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(CASE_CODE_ZONE).toInstant();
    }

    private static Instant startOfDayExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(CASE_CODE_ZONE).toInstant();
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
    public SubmitterSubmissionResponse forSubmitter(UUID id, String submitterId) {
        Submission submission = submissions.findById(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        if (!submission.getSubmitterId().equals(submitterId)) {
            throw new AccessDeniedException("Submitters may only read their own submissions.");
        }
        return SubmitterSubmissionResponse.from(submission);
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
                .findAllByStageOrderBySubmittedAtDescIdAsc(SubmissionStage.SUBMITTED, pageable)
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

    private void applyIntakeDetails(Submission submission, CreateSubmissionRequest request) {
        String department = clean(request.department());
        if (submission.getCategory() == SubmissionCategory.SERVICE_REQUEST && department == null) {
            department = suggestDepartment(clean(request.serviceRequested()));
        }
        submission.applyIntakeDetails(
                clean(request.submitterPhone()),
                clean(request.serviceRequested()),
                request.serviceRequester(),
                department,
                clean(request.expectedResult()),
                clean(request.acceptedRulesVersion())
        );
    }

    private String suggestDepartment(String serviceRequested) {
        if (serviceRequested == null) {
            return null;
        }
        String normalized = serviceRequested.toLowerCase(Locale.ROOT);
        return sectorsCatalog.activeSectors().stream()
                .map(SectorResponse::name)
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> normalized.contains(name.toLowerCase(Locale.ROOT))
                        || name.toLowerCase(Locale.ROOT).contains(normalized))
                .findFirst()
                .orElse(null);
    }

    private void notifySubmitter(
            Submission submission,
            String eventType,
            String subject,
            String message,
            String actor,
            Instant occurredAt
    ) {
        String recipient = isEmailAddress(submission.getSubmitterEmail())
                ? submission.getSubmitterEmail()
                : emailFromSubmitterId(submission.getSubmitterId());
        if (recipient == null) {
            return;
        }
        notificationOutbox.recordSubmitterNotification(
                submission.getId(),
                eventType,
                submission.getCode(),
                recipient,
                subject,
                message,
                actor,
                occurredAt
        );
    }

    /**
     * Records the reception-facing intake notification. Delivery is skipped when
     * the reception mailbox is not configured, so local and test environments do
     * not accumulate undeliverable outbox rows.
     */
    private void notifyReception(
            Submission submission,
            String eventType,
            String subject,
            String message,
            String actor,
            Instant occurredAt
    ) {
        if (!isEmailAddress(receptionEmail)) {
            return;
        }
        notificationOutbox.recordSubmitterNotification(
                submission.getId(),
                eventType,
                submission.getCode(),
                receptionEmail.trim(),
                subject,
                message,
                actor,
                occurredAt
        );
    }

    private void resolveOpenInformationItems(UUID submissionId, String actor, Instant now) {
        List<MissingItem> openItems = missingItems
                .findAllBySubmissionIdAndKindAndStatusOrderByRequestedAtAscIdAsc(
                        submissionId,
                        MissingItemKind.INFORMATION,
                        MissingItemStatus.OPEN
                );
        if (openItems.isEmpty()) {
            return;
        }
        for (MissingItem item : openItems) {
            item.resolve(actor, "Submitter replied with the requested information.", now);
        }
        missingItems.saveAll(openItems);
    }

    private void resolveOpenDocumentItems(
            UUID submissionId,
            DocumentType documentType,
            String documentName,
            String actor,
            Instant now
    ) {
        List<MissingItem> openItems = missingItems
                .findAllBySubmissionIdAndKindAndDocumentTypeAndStatus(
                        submissionId,
                        MissingItemKind.DOCUMENT,
                        documentType,
                        MissingItemStatus.OPEN
                );
        if (openItems.isEmpty()) {
            return;
        }
        for (MissingItem item : openItems) {
            item.resolve(actor, "Document provided: " + documentName, now);
        }
        missingItems.saveAll(openItems);
    }

    private static MissingItem createMissingItem(
            UUID submissionId,
            MissingItemRequest item,
            String actor,
            Instant now
    ) {
        if (item.label() == null || item.label().isBlank()) {
            throw new InvalidSubmissionException("A label is required for every missing item.");
        }
        String label = item.label().trim();
        String note = clean(item.note());
        String reason = clean(item.reason());
        String message = clean(item.message());
        return item.documentType() == null
                ? MissingItem.information(
                        UUID.randomUUID(),
                        submissionId,
                        label,
                        note,
                        reason,
                        item.dueAt(),
                        message,
                        actor,
                        now
                )
                : MissingItem.document(
                        UUID.randomUUID(),
                        submissionId,
                        label,
                        note,
                        reason,
                        item.dueAt(),
                        message,
                        item.documentType(),
                        actor,
                        now
                );
    }

    private static void requireCategoryDetails(CreateSubmissionRequest request) {
        boolean studentDetails = hasStudentDetails(request);
        boolean serviceDetails = hasServiceDetails(request);
        if (request.category() == SubmissionCategory.STUDENT) {
            if (serviceDetails) {
                throw new InvalidSubmissionException(
                        "Service-request details may only be provided for service requests."
                );
            }
            return;
        }
        if (studentDetails) {
            throw new InvalidSubmissionException(
                    "Student-specific details may only be provided for student submissions."
            );
        }
        if (request.category() == SubmissionCategory.GENERAL && serviceDetails) {
            throw new InvalidSubmissionException(
                    "Service-request details may only be provided for service requests."
            );
        }
    }

    private static boolean hasStudentDetails(CreateSubmissionRequest request) {
        return clean(request.studentNumber()) != null
                || clean(request.programme()) != null
                || clean(request.supervisor()) != null
                || request.recommendationLetterStatus() != null;
    }

    private static boolean hasServiceDetails(CreateSubmissionRequest request) {
        return clean(request.serviceRequested()) != null
                || request.serviceRequester() != null
                || clean(request.department()) != null
                || clean(request.expectedResult()) != null;
    }

    static void requireSubmitterOwnership(Submission submission, String actor, boolean submitter) {
        if (submitter) {
            if (!submission.getSubmitterId().equals(actor)) {
                throw new AccessDeniedException("Submitters may only modify their own submissions.");
            }
            if (submission.getStage() != SubmissionStage.DRAFT) {
                throw new AccessDeniedException("Submitters may only modify their own draft submissions.");
            }
        }
    }

    private static void requireSubmitterReplyOwnership(Submission submission, String actor) {
        if (!submission.getSubmitterId().equals(actor)) {
            throw new AccessDeniedException("Submitters may only reply to their own submissions.");
        }
        if (submission.getStage() != SubmissionStage.AWAITING_INFORMATION) {
            throw new SubmissionConflictException("Only a submission awaiting information can be replied to.");
        }
    }

    private static void requireSubmitterDocumentOwnership(
            Submission submission,
            String actor,
            boolean submitter
    ) {
        if (!submitter) {
            return;
        }
        if (!submission.getSubmitterId().equals(actor)) {
            throw new AccessDeniedException("Submitters may only attach documents to their own submissions.");
        }
        if (submission.getStage() != SubmissionStage.DRAFT
                && submission.getStage() != SubmissionStage.SUBMITTED
                && submission.getStage() != SubmissionStage.AWAITING_INFORMATION) {
            throw new AccessDeniedException(
                    "Submitters may only attach documents before the submission is forwarded."
            );
        }
    }

    private static String emailFromSubmitterId(String submitterId) {
        return isEmailAddress(submitterId) ? submitterId : null;
    }

    static boolean isEmailAddress(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
            return email.equals(address.getAddress());
        } catch (AddressException exception) {
            return false;
        }
    }

    private static void requireSubmitterRecommendationStatus(
            CreateSubmissionRequest request,
            boolean submitter
    ) {
        if (submitter && request.recommendationLetterStatus() == RecommendationLetterStatus.VERIFIED) {
            throw new AccessDeniedException("Submitters cannot mark a recommendation letter as verified.");
        }
    }

    static void requireSubmitterDocumentStatus(DocumentStatus status, boolean submitter) {
        if (submitter && status != DocumentStatus.SUBMITTED) {
            throw new AccessDeniedException("Submitters may only mark documents as submitted.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static int caseYear(Instant receivedAt) {
        return receivedAt.atZone(CASE_CODE_ZONE).getYear();
    }
}
