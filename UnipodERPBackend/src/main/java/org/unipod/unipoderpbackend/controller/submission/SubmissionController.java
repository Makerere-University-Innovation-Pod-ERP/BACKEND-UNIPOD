package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.submission.AdvanceSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.ConfirmProjectRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.DraftSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.ForwardSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsResponse;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckLinesRequest;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckResponse;
import org.unipod.unipoderpbackend.dto.submission.RejectSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.RequestInfoAliasRequest;
import org.unipod.unipoderpbackend.dto.submission.ReviewerAssignmentRequest;
import org.unipod.unipoderpbackend.dto.submission.RequestInformationRequest;
import org.unipod.unipoderpbackend.dto.submission.SendReminderRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionConfirmationResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.WalkInSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.WalkInSubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.security.SubmitterAuthorization;
import org.unipod.unipoderpbackend.service.submission.InformationResponseService;
import org.unipod.unipoderpbackend.service.submission.ReceptionChecklistService;
import org.unipod.unipoderpbackend.service.submission.SubmissionReminderService;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;
import org.unipod.unipoderpbackend.service.submission.WalkInSubmissionService;

@RestController
@RequestMapping("/submissions")
@Validated
public class SubmissionController {
    private final SubmissionService submissions;
    private final ReceptionChecklistService checklists;
    private final SubmissionReminderService reminders;
    private final InformationResponseService informationResponses;
    private final WalkInSubmissionService walkIns;

    public SubmissionController(
            SubmissionService submissions,
            ReceptionChecklistService checklists,
            SubmissionReminderService reminders,
            InformationResponseService informationResponses,
            WalkInSubmissionService walkIns
    ) {
        this.submissions = submissions;
        this.checklists = checklists;
        this.reminders = reminders;
        this.informationResponses = informationResponses;
        this.walkIns = walkIns;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse create(
            @Valid @RequestBody CreateSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.create(request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    /**
     * Saves a new brief as a draft. Drafts are private to the submitter, carry
     * no tracking reference and are not visible in the reception queue.
     */
    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse createDraft(
            @Valid @RequestBody DraftSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.createDraft(request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    /**
     * Autosaves a draft. Returns 409 once the brief has left the DRAFT stage.
     */
    @PatchMapping("/drafts/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse patchDraft(
            @PathVariable UUID id,
            @Valid @RequestBody DraftSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.patchDraft(id, request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    /**
     * Reception-assisted intake for a submitter who walks in without an
     * account. The brief is submitted immediately and appears in the reception
     * queue.
     */
    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public WalkInSubmissionResponse walkIn(
            @Valid @RequestBody WalkInSubmissionRequest request,
            Authentication authentication
    ) {
        return walkIns.submit(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.updateDraft(id, request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'OBSERVER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public PageResponse<SubmissionResponse> all(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SubmissionStage stage,
            @RequestParam(required = false) UUID sector,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.search(
                parseCategory(category), stage, sector, q, receivedFrom, receivedTo,
                pageable(page, size), authentication
        ));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'OBSERVER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public PageResponse<SubmissionResponse> search(
            @RequestParam @NotBlank String code,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.searchByCodeOrTitle(code, pageable(page, size), authentication));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public PageResponse<SubmissionResponse> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.mine(authentication.getName(), pageable(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public CaseFileResponse caseFile(@PathVariable UUID id) {
        return submissions.caseFile(id);
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse register(@PathVariable UUID id, Authentication authentication) {
        return submissions.register(id, authentication.getName());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse submit(@PathVariable UUID id, Authentication authentication) {
        return submissions.submit(
                id,
                authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication)
        );
    }

    /**
     * Saves the four named reception verification lines. Forwarding stays
     * locked until every applicable line is checked.
     */
    @PutMapping("/{id}/reception-check")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public ReceptionCheckResponse receptionCheck(
            @PathVariable UUID id,
            @Valid @RequestBody ReceptionCheckLinesRequest request,
            Authentication authentication
    ) {
        return checklists.updateLines(id, request, authentication);
    }

    /**
     * R3 request-more-information action: a list of missing items, a due date
     * and a submitter-facing message.
     */
    @PostMapping("/{id}/request-info")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER')")
    public SubmissionResponse requestInfo(
            @PathVariable UUID id,
            @Valid @RequestBody RequestInfoAliasRequest request,
            Authentication authentication
    ) {
        return submissions.requestInformation(id, request, authentication);
    }

    /**
     * Reminds the submitter about the open missing-information request through
     * both the email outbox and the in-app notification bell.
     */
    @PostMapping("/{id}/remind")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', "
            + "'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER')")
    public MissingItemsResponse remind(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SendReminderRequest request,
            Authentication authentication
    ) {
        return reminders.remind(
                id,
                request == null ? new SendReminderRequest(null) : request,
                authentication
        );
    }

    /**
     * The submitter's answer to a more-information request: upload the missing
     * files together with a reply note, returning the brief to the reception
     * queue. Each file names the open requested item it answers so the file is
     * not typed from its upload position.
     */
    @PostMapping(path = "/{id}/respond-info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmissionResponse respondInfo(
            @PathVariable UUID id,
            @RequestParam(required = false) String note,
            @RequestParam(name = "documentItemIds", required = false) List<UUID> documentItemIds,
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            @RequestParam(name = "file", required = false) List<MultipartFile> singleFiles,
            Authentication authentication
    ) {
        if (files != null && !files.isEmpty() && singleFiles != null && !singleFiles.isEmpty()) {
            throw new InvalidSubmissionException(
                    "Send the missing files with either the 'files' or the 'file' parameter, not both."
            );
        }
        List<MultipartFile> uploads = new ArrayList<>();
        if (files != null) {
            uploads.addAll(files);
        }
        if (singleFiles != null) {
            uploads.addAll(singleFiles);
        }
        return informationResponses.respond(id, note, documentItemIds, uploads, authentication);
    }

    /**
     * What the submitter sees after submitting: tracking reference, submission
     * time, destination department and its WhatsApp group invite link.
     */
    @GetMapping("/{id}/confirmation")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', "
            + "'FACT_FINDING_OFFICER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public SubmissionConfirmationResponse confirmation(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return submissions.confirmation(id, authentication);
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmissionResponse reply(
            @PathVariable UUID id,
            @Valid @RequestBody ReplySubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.reply(id, request, authentication.getName());
    }

    @PostMapping("/{id}/confirm-project")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse confirmProject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ConfirmProjectRequest request,
            Authentication authentication
    ) {
        return submissions.confirmAsProject(
                id,
                request == null ? new ConfirmProjectRequest(null, null) : request,
                authentication.getName()
        );
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.reject(id, request, authentication.getName());
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse forward(
            @PathVariable UUID id,
            @Valid @RequestBody ForwardSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.forward(id, request.sectorId(), request.note(), authentication.getName());
    }

    @PostMapping("/{id}/request-information")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER')")
    public SubmissionResponse requestInformation(
            @PathVariable UUID id,
            @Valid @RequestBody RequestInformationRequest request,
            Authentication authentication
    ) {
        return submissions.requestInformation(id, request, authentication);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR')")
    public SubmissionResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody AdvanceSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.advance(
                id,
                SubmissionStage.valueOf(request.stage().name()),
                authentication.getName()
        );
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public DocumentResponse addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDocumentRequest request,
            Authentication authentication
    ) {
        return submissions.addDocument(id, request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    @PutMapping("/{id}/reviewer")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SubmissionResponse assignReviewer(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewerAssignmentRequest request
    ) {
        return submissions.assignReviewer(id, request.subject());
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(page, size);
    }

    private static SubmissionCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SubmissionCategory.fromJson(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSubmissionException("The category is invalid.");
        }
    }

}
