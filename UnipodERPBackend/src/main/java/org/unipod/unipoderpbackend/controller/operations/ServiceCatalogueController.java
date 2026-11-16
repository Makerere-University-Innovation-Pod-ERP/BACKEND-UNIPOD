package org.unipod.unipoderpbackend.controller.operations;

import java.util.List;
import java.util.Map;
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
}

