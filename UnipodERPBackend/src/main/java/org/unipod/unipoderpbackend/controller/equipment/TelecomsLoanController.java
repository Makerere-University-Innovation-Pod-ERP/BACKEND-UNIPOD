package org.unipod.unipoderpbackend.controller.equipment;

import jakarta.validation.Valid;
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
import org.unipod.unipoderpbackend.dto.equipment.ApproveLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.DeclineLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.ItemsResponse;
import org.unipod.unipoderpbackend.dto.equipment.LoanResponse;
import org.unipod.unipoderpbackend.dto.equipment.RequestLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReturnLoanRequest;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.equipment.LoanService;

/** Telecoms equipment lending (frontend `services/telecoms.js`): request, approve or decline, return. */
@RestController
@RequestMapping("/telecoms/equipment/loans")
@Validated
public class TelecomsLoanController {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.TELECOMS;

    private final LoanService loans;
    private final CurrentActor currentActor;

    public TelecomsLoanController(LoanService loans, CurrentActor currentActor) {
        this.loans = loans;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<LoanResponse> list() {
        return new ItemsResponse<>(loans.list(DEPARTMENT));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.REPORT)
    public LoanResponse request(@Valid @RequestBody RequestLoanRequest request, Authentication authentication) {
        return loans.request(currentActor.from(authentication), DEPARTMENT, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public LoanResponse approve(@PathVariable String id, @Valid @RequestBody(required = false) ApproveLoanRequest request,
                                Authentication authentication) {
        return loans.approve(currentActor.from(authentication), DEPARTMENT, id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/decline")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public LoanResponse decline(@PathVariable String id, @Valid @RequestBody(required = false) DeclineLoanRequest request,
                                Authentication authentication) {
        return loans.decline(currentActor.from(authentication), DEPARTMENT, id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public LoanResponse markReturned(@PathVariable String id, @Valid @RequestBody ReturnLoanRequest request,
                                     Authentication authentication) {
        return loans.markReturned(currentActor.from(authentication), DEPARTMENT, id, request.condition());
    }
}
