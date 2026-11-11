package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.submission.MissingItemResponse;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsRequest;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsResponse;
import org.unipod.unipoderpbackend.dto.submission.ResolveMissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.SendReminderRequest;
import org.unipod.unipoderpbackend.service.submission.MissingItemService;

@RestController
@RequestMapping("/submissions/{id}")
@Validated
public class MissingItemController {
    private static final String OPERATIONAL_ROLES =
            "hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', "
                    + "'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER')";

    private final MissingItemService missingItems;

    public MissingItemController(MissingItemService missingItems) {
        this.missingItems = missingItems;
    }

    @GetMapping("/missing-items")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', "
            + "'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER', 'OBSERVER')")
    public MissingItemsResponse list(@PathVariable UUID id) {
        return missingItems.list(id);
    }

    /**
     * Records outstanding information and/or document requests. A missing item
     * with a {@code documentType} is a document request; otherwise it is an
     * information request.
     */
    @PostMapping("/missing-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OPERATIONAL_ROLES)
    public MissingItemsResponse addMissingItems(
            @PathVariable UUID id,
            @Valid @RequestBody MissingItemsRequest request,
            Authentication authentication
    ) {
        return missingItems.addMissingItems(id, request, authentication.getName());
    }

    @PostMapping("/missing-documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OPERATIONAL_ROLES)
    public MissingItemsResponse addMissingDocuments(
            @PathVariable UUID id,
            @Valid @RequestBody MissingItemsRequest request,
            Authentication authentication
    ) {
        return missingItems.addMissingDocuments(id, request, authentication.getName());
    }

    @PostMapping("/missing-items/{itemId}/resolve")
    @PreAuthorize(OPERATIONAL_ROLES)
    public MissingItemResponse resolve(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) ResolveMissingItemRequest request,
            Authentication authentication
    ) {
        return missingItems.resolve(
                id,
                itemId,
                request == null ? new ResolveMissingItemRequest(null) : request,
                authentication.getName()
        );
    }

    @PostMapping("/reminders")
    @PreAuthorize(OPERATIONAL_ROLES)
    public MissingItemsResponse sendReminder(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SendReminderRequest request,
            Authentication authentication
    ) {
        return missingItems.sendReminder(
                id,
                request == null ? new SendReminderRequest(null) : request,
                authentication.getName()
        );
    }
}
