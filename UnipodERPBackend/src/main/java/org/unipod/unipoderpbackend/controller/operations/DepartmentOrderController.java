package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unipod.unipoderpbackend.dto.operations.DepartmentOrderQuoteRequest;
import org.unipod.unipoderpbackend.dto.operations.DepartmentOrderRequest;
import org.unipod.unipoderpbackend.dto.operations.DepartmentOrderStageRequest;
import org.unipod.unipoderpbackend.service.operations.DepartmentOrderService;

@RestController
@RequestMapping("/departments/{dept}/orders")
public class DepartmentOrderController {
    private final DepartmentOrderService orders;
    public DepartmentOrderController(DepartmentOrderService orders) { this.orders = orders; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> create(@PathVariable String dept, @Valid @RequestBody DepartmentOrderRequest request, Authentication auth) {
        return orders.create(dept, request, auth.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','RECEPTIONIST','SECTOR_OFFICER','FINANCE_OFFICER','OBSERVER','FACT_FINDING_OFFICER','REVIEWER')")
    public Map<String, Object> list(@PathVariable String dept) { return orders.listKanban(dept); }

    @PostMapping("/{id}/quote")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER','FINANCE_OFFICER')")
    public Map<String, Object> quote(@PathVariable String dept, @PathVariable UUID id, @Valid @RequestBody DepartmentOrderQuoteRequest request, Authentication auth) {
        return orders.quote(dept, id, request, auth.getName());
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String, Object> stage(@PathVariable String dept, @PathVariable UUID id, @Valid @RequestBody DepartmentOrderStageRequest request, Authentication auth) {
        return orders.changeStage(dept, id, request, auth.getName());
    }
}

