package org.unipod.unipoderpbackend.service.textile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.textile.TextileOrderRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.equipment.MachineRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.repository.finance.PriceListItemRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentOrderRepository;
import org.unipod.unipoderpbackend.repository.operations.StockItemRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.PriceListService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.service.stock.StockService;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;
import tools.jackson.databind.ObjectMapper;

class TextileOrderServiceValidationTest {
    @Test
    void submittedOrderCannotHavePastDateNeeded() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneId.of("UTC"));
        TextileOrderService service = new TextileOrderService(
                mock(DepartmentOrderRepository.class),
                mock(StockItemRepository.class),
                mock(MachineRepository.class),
                mock(PriceListItemRepository.class),
                mock(InvoiceRepository.class),
                mock(PriceListService.class),
                mock(SectorService.class),
                mock(DepartmentAccess.class),
                mock(StockService.class),
                mock(DocumentStorage.class),
                mock(AuditTrail.class),
                new ObjectMapper(),
                clock);
        TextileOrderRequest request = new TextileOrderRequest(
                "Customer", "Public", "Curtains", "2 panels", "Blue cotton",
                LocalDate.of(2026, 11, 20), true, true);

        assertThrows(InvalidOperationsRequestException.class, () -> service.create(
                request, null, new Actor("reception@example.test", ApiUserRole.RECEPTIONIST, Set.of())));
    }
}
