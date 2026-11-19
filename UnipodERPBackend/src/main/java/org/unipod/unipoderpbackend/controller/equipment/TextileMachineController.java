package org.unipod.unipoderpbackend.controller.equipment;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.equipment.CreateMachineRequest;
import org.unipod.unipoderpbackend.dto.equipment.ItemsResponse;
import org.unipod.unipoderpbackend.dto.equipment.MachineFaultReportResponse;
import org.unipod.unipoderpbackend.dto.equipment.MachineResponse;
import org.unipod.unipoderpbackend.dto.equipment.MarkMachineWorkingRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReportMachineRequest;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.equipment.MachineService;
import org.unipod.unipoderpbackend.service.equipment.PhotoMeta;

/** The textile workshop machines (frontend `services/textile.js`). */
@RestController
@RequestMapping("/textile/machines")
@Validated
public class TextileMachineController {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.TEXTILE;

    private final MachineService machines;
    private final CurrentActor currentActor;

    public TextileMachineController(MachineService machines, CurrentActor currentActor) {
        this.machines = machines;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<MachineResponse> list() {
        return new ItemsResponse<>(machines.list(DEPARTMENT).stream().map(MachineResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public MachineResponse register(@Valid @RequestBody CreateMachineRequest request, Authentication authentication) {
        return MachineResponse.from(machines.register(currentActor.from(authentication), DEPARTMENT, request));
    }

    /** Multipart, as the screen sends it: a JSON part named `report` and an optional `photo` file. */
    @PostMapping(value = "/{id}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.REPORT)
    public MachineFaultReportResponse report(
            @PathVariable UUID id,
            @Valid @RequestPart(value = "report", required = false) ReportMachineRequest report,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            Authentication authentication) {
        if (report == null) {
            // Optional in the signature because the shared error handler has no mapping for a missing part (it would be a 500).
            throw new InvalidEquipmentException("The report is required.");
        }
        PhotoMeta meta = photo == null || photo.isEmpty() ? null : new PhotoMeta(photo.getOriginalFilename(), photo.getContentType());
        MachineService.ReportResult result = machines.report(currentActor.from(authentication), DEPARTMENT, id, report, meta);
        return MachineFaultReportResponse.of(MachineResponse.from(result.machine()), result.report(), result.managersNotified());
    }

    @PostMapping("/{id}/mark-working")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public MachineResponse markWorking(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) MarkMachineWorkingRequest request,
            Authentication authentication) {
        return MachineResponse.from(machines.markWorking(currentActor.from(authentication), DEPARTMENT, id,
                request == null ? null : request.note()));
    }
}
