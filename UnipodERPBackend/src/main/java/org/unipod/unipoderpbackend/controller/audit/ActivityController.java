package org.unipod.unipoderpbackend.controller.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.audit.ActivityFilter;
import org.unipod.unipoderpbackend.dto.audit.AuditEntryResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.audit.AuditService;

@RestController
@Validated
public class ActivityController {
    // Same roles as GET /cases/{id} in CaseFileController: if you can open the brief, you can see its history.
    private static final String CASE_READ_ROLES =
            "hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'FACT_FINDING_OFFICER', "
                    + "'REVIEWER', 'SECTOR_OFFICER', 'IP_LIAISON', 'SUBMITTER', 'OBSERVER')";

    private final AuditService audit;

    public ActivityController(AuditService audit) {
        this.audit = audit;
    }

    /** AD2 audit log. No @PreAuthorize on purpose: authorize() decides, and a refusal comes back with a code. */
    @GetMapping("/activity")
    public PageResponse<AuditEntryResponse> activity(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication
    ) {
        return audit.search(
                CurrentActor.from(authentication),
                new ActivityFilter(actor, action, entityType, entityId, from, to),
                page,
                size
        );
    }

    @GetMapping("/cases/{id}/activity")
    @PreAuthorize(CASE_READ_ROLES)
    public PageResponse<AuditEntryResponse> caseActivity(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return audit.caseActivity(id, authentication, page, size);
    }
}
