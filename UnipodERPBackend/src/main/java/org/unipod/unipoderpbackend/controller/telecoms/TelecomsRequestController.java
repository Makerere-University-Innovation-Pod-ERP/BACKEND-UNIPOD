package org.unipod.unipoderpbackend.controller.telecoms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsPaymentConfirmationRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsPriceListItemResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsPriceListResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsQuoteRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestListResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestMessage;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestPayload;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestReturn;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestStatusRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsWorkDetailsRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.telecoms.TelecomsRequestService;

@RestController
@RequestMapping("/telecoms")
@Validated
public class TelecomsRequestController {
    private static final String READ_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'SUBMITTER', 'FINANCE_OFFICER', 'OBSERVER', "
            + "'TELECOMS_DOMAIN_OWNER')";
    private static final String REQUESTER_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'SUBMITTER', 'FINANCE_OFFICER', "
            + "'TELECOMS_DOMAIN_OWNER')";
    private static final String MANAGE_ROLES =
            "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', 'TELECOMS_DOMAIN_OWNER')";
    private static final String FINANCE_ROLES = "hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')";

    private final TelecomsRequestService requests;
    private final CurrentActor currentActor;

    public TelecomsRequestController(TelecomsRequestService requests, CurrentActor currentActor) {
        this.requests = requests;
        this.currentActor = currentActor;
    }

    @GetMapping("/requests")
    @PreAuthorize(READ_ROLES)
    public TelecomsRequestListResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return requests.list(currentActor.from(authentication), page, size);
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize(READ_ROLES)
    public TelecomsRequestResponse get(@PathVariable UUID id, Authentication authentication) {
        return requests.get(id, currentActor.from(authentication));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(REQUESTER_ROLES)
    public TelecomsRequestResponse create(
            @Valid @RequestBody TelecomsRequestPayload payload,
            Authentication authentication
    ) {
        return requests.create(payload, currentActor.from(authentication));
    }

    @PutMapping("/requests/{id}/draft")
    @PreAuthorize(REQUESTER_ROLES)
    public TelecomsRequestResponse updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsRequestPayload payload,
            Authentication authentication
    ) {
        return requests.updateDraft(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/submit")
    @PreAuthorize(REQUESTER_ROLES)
    public TelecomsRequestResponse submitDraft(@PathVariable UUID id, Authentication authentication) {
        return requests.submitDraft(id, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/quote")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse sendQuote(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsQuoteRequest payload,
            Authentication authentication
    ) {
        return requests.sendQuote(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/confirm-payment")
    @PreAuthorize(FINANCE_ROLES)
    public TelecomsRequestResponse confirmPayment(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsPaymentConfirmationRequest payload,
            Authentication authentication
    ) {
        return requests.confirmPayment(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/request-details")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse requestDetails(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsRequestMessage payload,
            Authentication authentication
    ) {
        return requests.requestDetails(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/send-back")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse sendBack(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsRequestReturn payload,
            Authentication authentication
    ) {
        return requests.sendBack(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/status")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsRequestStatusRequest payload,
            Authentication authentication
    ) {
        return requests.updateStatus(id, payload, currentActor.from(authentication));
    }

    @PutMapping("/requests/{id}/work-details")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse updateWorkDetails(
            @PathVariable UUID id,
            @Valid @RequestBody TelecomsWorkDetailsRequest payload,
            Authentication authentication
    ) {
        return requests.updateWorkDetails(id, payload, currentActor.from(authentication));
    }

    @PostMapping("/requests/{id}/mark-done")
    @PreAuthorize(MANAGE_ROLES)
    public TelecomsRequestResponse markDone(@PathVariable UUID id, Authentication authentication) {
        return requests.markDone(id, currentActor.from(authentication));
    }

    @GetMapping("/price-list")
    @PreAuthorize(READ_ROLES)
    public TelecomsPriceListResponse priceList(Authentication authentication) {
        List<TelecomsPriceListItemResponse> items = requests.priceList(currentActor.from(authentication));
        return new TelecomsPriceListResponse(items);
    }
}
