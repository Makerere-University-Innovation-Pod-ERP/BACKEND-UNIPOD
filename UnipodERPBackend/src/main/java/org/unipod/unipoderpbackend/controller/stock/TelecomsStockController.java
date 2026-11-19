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
import org.unipod.unipoderpbackend.dto.stock.CreateStockItemRequest;
import org.unipod.unipoderpbackend.dto.stock.MovementResult;
import org.unipod.unipoderpbackend.dto.stock.RecordTelecomsMovementRequest;
import org.unipod.unipoderpbackend.dto.stock.StockItemResponse;
import org.unipod.unipoderpbackend.dto.stock.TelecomsStockMovementResponse;
import org.unipod.unipoderpbackend.dto.stock.UpdateMinimumRequest;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.stock.StockService;

/** The telecoms lab stock list and its history (frontend `services/telecoms.js`). */
@RestController
@RequestMapping("/telecoms/stock")
@Validated
public class TelecomsStockController {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.TELECOMS;

    private final StockService stock;
    private final CurrentActor currentActor;

    public TelecomsStockController(StockService stock, CurrentActor currentActor) {
        this.stock = stock;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<StockItemResponse> list() {
        return new ItemsResponse<>(stock.list(DEPARTMENT).stream().map(StockItemResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public StockItemResponse register(@Valid @RequestBody CreateStockItemRequest request, Authentication authentication) {
        return StockItemResponse.from(stock.register(currentActor.from(authentication), DEPARTMENT, request));
    }

    @GetMapping("/movements")
    @PreAuthorize(EquipmentRoles.READ)
    public ItemsResponse<TelecomsStockMovementResponse> movements() {
        return new ItemsResponse<>(stock.history(DEPARTMENT).stream()
                .map(view -> TelecomsStockMovementResponse.from(view.movement(), view.itemName())).toList());
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EquipmentRoles.MANAGE)
    public MovementResult<TelecomsStockMovementResponse> record(@Valid @RequestBody RecordTelecomsMovementRequest request,
                                                                Authentication authentication) {
        StockService.Recorded recorded = stock.record(currentActor.from(authentication), DEPARTMENT,
                new StockService.MovementCommand(request.itemId(), request.type(), request.quantity(), request.note(),
                        request.requestId()));
        return new MovementResult<>(TelecomsStockMovementResponse.from(recorded.movement(), recorded.itemName()),
                StockItemResponse.from(recorded.item()));
    }

    @PatchMapping("/{id}/minimum")
    @PreAuthorize(EquipmentRoles.MANAGE)
    public StockItemResponse updateMinimum(@PathVariable UUID id, @Valid @RequestBody UpdateMinimumRequest request,
                                           Authentication authentication) {
        return StockItemResponse.from(stock.changeMinimum(currentActor.from(authentication), DEPARTMENT, id,
                request.minimum()));
    }
}
