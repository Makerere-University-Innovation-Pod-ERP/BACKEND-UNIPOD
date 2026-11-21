package org.unipod.unipoderpbackend.controller.stock;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.controller.equipment.EquipmentRoles;
import org.unipod.unipoderpbackend.dto.equipment.ItemsResponse;
import org.unipod.unipoderpbackend.dto.stock.CncStockMovementResponse;
import org.unipod.unipoderpbackend.dto.stock.CncStockResponse;
import org.unipod.unipoderpbackend.dto.stock.CreateStockItemRequest;
import org.unipod.unipoderpbackend.dto.stock.MovementResult;
import org.unipod.unipoderpbackend.dto.stock.RecordCncMovementRequest;
import org.unipod.unipoderpbackend.dto.stock.StockItemResponse;
import org.unipod.unipoderpbackend.dto.stock.UpdateMinimumRequest;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.stock.StockService;

@RestController
@RequestMapping("/cnc/stock")
@Validated
public class CncStockController {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.CNC;

    private final StockService stock;
    private final CurrentActor currentActor;

    public CncStockController(StockService stock, CurrentActor currentActor) {
        this.stock = stock;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(EquipmentRoles.READ)
    public CncStockResponse list() {
        return new CncStockResponse(stock.list(DEPARTMENT).stream().map(StockItemResponse::from).toList(),
                stock.movementsThisWeek(DEPARTMENT));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public StockItemResponse register(@Valid @RequestBody CreateStockItemRequest request, Authentication authentication) {
        return StockItemResponse.from(stock.register(currentActor.from(authentication), DEPARTMENT, request));
    }

    @GetMapping("/movements")
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<CncStockMovementResponse> movements() {
        return new ItemsResponse<>(stock.history(DEPARTMENT).stream()
                .map(view -> CncStockMovementResponse.from(view.movement(), view.itemName())).toList());
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public MovementResult<CncStockMovementResponse> record(@Valid @RequestBody RecordCncMovementRequest request,
                                                           Authentication authentication) {
        StockService.Recorded recorded = stock.record(currentActor.from(authentication), DEPARTMENT,
                new StockService.MovementCommand(request.itemId(), request.type(), request.quantity(), request.note(),
                        request.jobId()));
        return new MovementResult<>(CncStockMovementResponse.from(recorded.movement(), recorded.itemName()),
                StockItemResponse.from(recorded.item()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public StockItemResponse updateMinimum(@PathVariable UUID id, @Valid @RequestBody UpdateMinimumRequest request,
                                           Authentication authentication) {
        return StockItemResponse.from(stock.changeMinimum(currentActor.from(authentication), DEPARTMENT, id,
                request.minimum()));
    }
}
