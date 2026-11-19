package org.unipod.unipoderpbackend.controller.finance;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.finance.InvoiceFromPriceRequest;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemRequest;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemResponse;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemUpdateRequest;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.finance.PriceListService;

@RestController
@RequestMapping("/price-list-items")
public class PriceListController {
    private final PriceListService priceList;
    private final CurrentActor currentActor;

    public PriceListController(PriceListService priceList, CurrentActor currentActor) {
        this.priceList = priceList;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PriceListItemResponse> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) PriceItemStatus status,
            Authentication authentication
    ) {
        return priceList.list(currentActor.from(authentication), departmentId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PriceListItemResponse create(@Valid @RequestBody PriceListItemRequest request, Authentication authentication) {
        return priceList.create(currentActor.from(authentication), request);
    }

    @PostMapping("/{id}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public InvoiceResponse invoice(
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceFromPriceRequest request,
            Authentication authentication
    ) {
        return priceList.invoice(currentActor.from(authentication), id, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PriceListItemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody PriceListItemUpdateRequest request,
            Authentication authentication
    ) {
        return priceList.update(currentActor.from(authentication), id, request);
    }
}
