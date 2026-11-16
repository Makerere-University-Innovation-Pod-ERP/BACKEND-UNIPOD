package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.unipod.unipoderpbackend.dto.operations.CreateServiceCatalogueItemRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.operations.ServiceCatalogueItemResponse;
import org.unipod.unipoderpbackend.service.operations.ServiceCatalogueService;

@RestController
@RequestMapping("/services")
public class ServiceCatalogueController {
    private final ServiceCatalogueService catalogue;
    public ServiceCatalogueController(ServiceCatalogueService catalogue) { this.catalogue = catalogue; }

    @GetMapping("/catalogue")
    public Map<String, List<ServiceCatalogueItemResponse>> catalogue() { return catalogue.catalogue(); }

    @PostMapping("/catalogue")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR')")
    public ServiceCatalogueItemResponse create(
            @Valid @RequestBody CreateServiceCatalogueItemRequest request,
            Authentication auth) {
        return catalogue.create(request, auth.getName());
    }
}

