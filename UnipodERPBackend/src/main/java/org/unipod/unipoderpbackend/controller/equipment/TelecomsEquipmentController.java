package org.unipod.unipoderpbackend.controller.equipment;

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
import org.unipod.unipoderpbackend.dto.equipment.CreateEquipmentRequest;
import org.unipod.unipoderpbackend.dto.equipment.ItemsResponse;
import org.unipod.unipoderpbackend.dto.equipment.MarkEquipmentWorkingRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReportEquipmentFaultRequest;
import org.unipod.unipoderpbackend.dto.equipment.TelecomsEquipmentResponse;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.equipment.EquipmentService;

/** The telecoms lab equipment register (frontend `services/telecoms.js`). Loans live in {@link TelecomsLoanController}. */
@RestController
@RequestMapping("/telecoms/equipment")
@Validated
public class TelecomsEquipmentController {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.TELECOMS;

    private final EquipmentService equipment;
    private final CurrentActor currentActor;

    public TelecomsEquipmentController(EquipmentService equipment, CurrentActor currentActor) {
        this.equipment = equipment;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<TelecomsEquipmentResponse> list() {
        return new ItemsResponse<>(equipment.list(DEPARTMENT).stream().map(TelecomsEquipmentResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public TelecomsEquipmentResponse register(@Valid @RequestBody CreateEquipmentRequest request,
                                              Authentication authentication) {
        return TelecomsEquipmentResponse.from(equipment.register(currentActor.from(authentication), DEPARTMENT, request));
    }

    @PostMapping("/{id}/mark-working")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public TelecomsEquipmentResponse markWorking(@PathVariable UUID id, @Valid @RequestBody(required = false) MarkEquipmentWorkingRequest request,
                          Authentication authentication) {
        return TelecomsEquipmentResponse.from(equipment.markWorking(currentActor.from(authentication), DEPARTMENT, id,
                request == null ? null : request.note()));
    }

    @PostMapping("/report-fault")
    @PreAuthorize(EquipmentRoles.REPORT)
    public TelecomsEquipmentResponse reportFault(@Valid @RequestBody ReportEquipmentFaultRequest request,
                                                 Authentication authentication) {
        return TelecomsEquipmentResponse.from(equipment.reportFault(currentActor.from(authentication), DEPARTMENT,
                request.equipmentId(), request.description()));
    }
}
