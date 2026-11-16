package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.operations.CreateServiceCatalogueItemRequest;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.ServiceCatalogueItem;
import org.unipod.unipoderpbackend.repository.operations.ServiceCatalogueItemRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

class ServiceCatalogueServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final String ACTOR = "admin@example.org";

    private final ServiceCatalogueItemRepository items = mock(ServiceCatalogueItemRepository.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final ServiceCatalogueService service = new ServiceCatalogueService(
            items, auditTrail, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createAuditsCatalogueItemIdDepartmentAndPrice() {
        when(items.saveAndFlush(any(ServiceCatalogueItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateServiceCatalogueItemRequest(
                DepartmentCode.CNC, "Laser cutting", null, 25000L, "UGX", null,
                null, true, null, null);

        var response = service.create(request, ACTOR);

        assertEquals(DepartmentCode.CNC.name(), response.department());
        verify(auditTrail).record(
                eq(ACTOR),
                eq("service_catalogue.created"),
                eq("service_catalogue"),
                eq(response.id()),
                eq(null),
                eq(AuditTrail.details("department", "CNC", "price", 25000L)));
    }
}
