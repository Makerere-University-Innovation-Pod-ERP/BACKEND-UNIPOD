package org.unipod.unipoderpbackend.service.textile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.unipod.unipoderpbackend.dto.textile.TextileOrderRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileMaterialLine;
import org.unipod.unipoderpbackend.dto.textile.TextileQualityRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileQuoteRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileTransitionRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;
import org.unipod.unipoderpbackend.model.finance.PriceUnit;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.DepartmentOrder;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;
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
import org.unipod.unipoderpbackend.model.sector.Sector;
import tools.jackson.databind.ObjectMapper;

class TextileOrderServiceAccessTest {
    private static final String STAFF = "textile@example.test";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneId.of("UTC"));

    private DepartmentOrderRepository orders;
    private StockService stock;
    private StockItemRepository stockItems;
    private PriceListService priceList;
    private SectorService sectors;
    private DocumentStorage storage;
    private TextileOrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(DepartmentOrderRepository.class);
        stock = mock(StockService.class);
        stockItems = mock(StockItemRepository.class);
        priceList = mock(PriceListService.class);
        sectors = mock(SectorService.class);
        storage = mock(DocumentStorage.class);
        service = new TextileOrderService(
                orders,
                stockItems,
                mock(MachineRepository.class),
                mock(PriceListItemRepository.class),
                mock(InvoiceRepository.class),
                priceList,
                sectors,
                mock(DepartmentAccess.class),
                stock,
                storage,
                mock(AuditTrail.class),
                new ObjectMapper(),
                CLOCK);
    }

    @Test
    void submitterCannotReadTextileOrders() {
        assertThrows(AccessDeniedException.class, () -> service.get(UUID.randomUUID(),
                new Actor("other@example.test", ApiUserRole.SUBMITTER, Set.of())));
        verify(orders, never()).findByIdAndDepartment(any(), eq(DepartmentCode.TEXTILE));
    }

    @Test
    void submitterCannotReadTheTextilePriceList() {
        assertThrows(AccessDeniedException.class, () -> service.priceList(
                new Actor("submitter@example.test", ApiUserRole.SUBMITTER, Set.of())));
        verify(sectors, never()).activeSectorByName("Textile");
    }

    @Test
    void receptionistCannotEditAnotherUsersDraft() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(order(id, "DRAFT", STAFF, "{}")));

        assertThrows(AccessDeniedException.class, () -> service.update(id,
                new TextileOrderRequest("Customer", "Public", "Curtains", "2 panels",
                        "Blue cotton", null, true, true),
                null,
                new Actor("other@example.test", ApiUserRole.RECEPTIONIST, Set.of())));
    }

    @Test
    void returnedOrderCanBeResubmittedWithoutChangingItsCode() {
        UUID id = UUID.randomUUID();
        DepartmentOrder returned = order(id, "RETURNED", STAFF, "{}");
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(returned));
        when(orders.saveAndFlush(any(DepartmentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(id,
                new TextileOrderRequest("Customer", "Public", "Curtains", "2 panels",
                        "Blue cotton", null, true, true),
                null,
                new Actor(STAFF, ApiUserRole.RECEPTIONIST, Set.of()));

        assertEquals("NEW", response.get("status"));
        assertEquals("TXO-2026-001", response.get("code"));
    }

    @Test
    void productionStartDeductsAggregatedMaterialsOnlyOnce() {
        UUID id = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        String specs = """
                {"customerName":"Customer","estimate":{"materials":[
                  {"itemId":"%s","quantity":1.5},{"itemId":"%s","quantity":2.5}],
                  "priceListItemId":"%s","priceQuantity":1}}
                """.formatted(stockId, stockId, UUID.randomUUID());
        DepartmentOrder scheduled = order(id, "SCHEDULED", STAFF, specs);
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(scheduled));
        when(orders.saveAndFlush(any(DepartmentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Actor manager = new Actor(STAFF, ApiUserRole.DEPARTMENT_COORDINATOR, Set.of());

        service.transition(id, new TextileTransitionRequest("BEING_MADE"), manager);
        assertEquals("BEING_MADE", scheduled.getStage());
        verify(stock, times(1)).record(eq(manager), eq(DepartmentCode.TEXTILE), any());
        var movement = new StockService.MovementCommand(
                stockId, StockMovementType.USED_ON_ORDER, new BigDecimal("4.0"),
                "Materials committed at production start", id.toString());
        verify(stock).record(eq(manager), eq(DepartmentCode.TEXTILE), eq(movement));

        assertThrows(org.unipod.unipoderpbackend.exception.operations.OperationsConflictException.class,
                () -> service.transition(id, new TextileTransitionRequest("BEING_MADE"), manager));
        verify(stock, times(1)).record(eq(manager), eq(DepartmentCode.TEXTILE), any());
    }

    @Test
    void quoteRejectsAnActivePriceOwnedByAnotherDepartment() {
        UUID id = UUID.randomUUID();
        UUID textileDepartment = UUID.randomUUID();
        UUID anotherDepartment = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(order(id, "NEW", STAFF, "{}")));
        when(sectors.activeSectorByName("Textile")).thenReturn(java.util.Optional.of(
                new Sector(textileDepartment, "Textile", "textile@example.test", true,
                        CLOCK.instant(), CLOCK.instant())));
        when(priceList.requireActive(priceId)).thenReturn(new PriceListItem(
                priceId, anotherDepartment, "Other service", PriceUnit.PER_ITEM, 1000,
                LocalDate.now(CLOCK), PriceItemStatus.ACTIVE, STAFF, CLOCK.instant()));

        assertThrows(org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException.class,
                () -> service.quote(id, quoteRequest(priceId, List.of(
                        new TextileMaterialLine(UUID.randomUUID(), BigDecimal.ONE))), manager()));
        verify(priceList, never()).invoiceFor(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void quoteAggregatesDuplicateLinesBeforeCheckingAvailability() {
        UUID id = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        UUID textileDepartment = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(order(id, "NEW", STAFF, "{}")));
        when(sectors.activeSectorByName("Textile")).thenReturn(java.util.Optional.of(
                new Sector(textileDepartment, "Textile", "textile@example.test", true,
                        CLOCK.instant(), CLOCK.instant())));
        when(priceList.requireActive(priceId)).thenReturn(new PriceListItem(
                priceId, textileDepartment, "Sewing", PriceUnit.PER_ITEM, 1000,
                LocalDate.now(CLOCK), PriceItemStatus.ACTIVE, STAFF, CLOCK.instant()));
        when(stockItems.findByIdForUpdate(stockId)).thenReturn(java.util.Optional.of(new StockItem(
                stockId, "TX-123", "Cotton", "TEXTILE", new BigDecimal("3"), "m",
                BigDecimal.ZERO, "", CLOCK.instant())));

        assertThrows(org.unipod.unipoderpbackend.exception.operations.OperationsConflictException.class,
                () -> service.quote(id, quoteRequest(priceId, List.of(
                        new TextileMaterialLine(stockId, new BigDecimal("2")),
                        new TextileMaterialLine(stockId, new BigDecimal("2")))), manager()));
        verify(stockItems, times(1)).findByIdForUpdate(stockId);
        verify(priceList, never()).invoiceFor(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void qualityRejectsUnknownChecklistIds() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(order(id, "BEING_MADE", STAFF, "{}")));

        assertThrows(InvalidOperationsRequestException.class, () -> service.saveQuality(id,
                new TextileQualityRequest(List.of(
                        new TextileQualityRequest.QualityItem("unknown", true, "Unknown"))), manager()));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void orderResponseDoesNotExposeCollectionRecorderEmail() {
        UUID id = UUID.randomUUID();
        String specs = """
                {"collection":{"collectedBy":"Customer","collectedAt":"2026-11-21T12:00:00Z",
                  "recordedBy":"staff@example.test"}}
                """;
        when(orders.findByIdAndDepartment(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(order(id, "COLLECTED", STAFF, specs)));

        Map<?, ?> collection = (Map<?, ?>) service.get(id, manager()).get("collection");

        assertEquals("Customer", collection.get("collectedBy"));
        assertFalse(collection.containsKey("recordedBy"));
    }

    @Test
    void createRollbackRemovesUploadedPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "reference.png", "image/png", new byte[]{1});
        when(storage.upload(photo)).thenReturn("uploaded-ref");
        when(orders.saveAndFlush(any())).thenThrow(new IllegalStateException("database write failed"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> service.create(
                    new TextileOrderRequest("Customer", "Public", "Curtains", "2 panels",
                            "Blue cotton", null, false, false),
                    photo, new Actor(STAFF, ApiUserRole.RECEPTIONIST, Set.of())));
        } finally {
            finishSynchronization(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(storage).delete("uploaded-ref");
    }

    @Test
    void photoReplacementDeletesTheOldReferenceOnlyAfterCommit() throws Exception {
        UUID id = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile("photo", "new.png", "image/png", new byte[]{1});
        DepartmentOrder draft = order(id, "DRAFT", STAFF,
                "{\"photo\":{\"name\":\"old.png\",\"reference\":\"old-ref\",\"contentType\":\"image/png\"}}");
        when(storage.upload(photo)).thenReturn("new-ref");
        when(orders.findByIdAndDepartmentForUpdate(id, DepartmentCode.TEXTILE))
                .thenReturn(java.util.Optional.of(draft));
        when(orders.saveAndFlush(any(DepartmentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.update(id,
                    new TextileOrderRequest("Customer", "Public", "Curtains", "2 panels",
                            "Blue cotton", null, false, false),
                    photo, new Actor(STAFF, ApiUserRole.RECEPTIONIST, Set.of()));
        } finally {
            finishSynchronization(TransactionSynchronization.STATUS_COMMITTED);
        }

        verify(storage).delete("old-ref");
        verify(storage, never()).delete("new-ref");
    }

    private void finishSynchronization(int status) {
        try {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(status));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private TextileQuoteRequest quoteRequest(UUID priceId, List<TextileMaterialLine> materials) {
        return new TextileQuoteRequest(materials, priceId, BigDecimal.ONE, LocalDate.now(CLOCK).plusDays(1));
    }

    private Actor manager() {
        return new Actor(STAFF, ApiUserRole.DEPARTMENT_COORDINATOR, Set.of());
    }

    private DepartmentOrder order(UUID id, String stage, String owner, String specs) {
        DepartmentOrder order = new DepartmentOrder(id, "TXO-2026-001", DepartmentCode.TEXTILE, owner,
                "Textile work", 1, null, "textile-rules-v1", stage, specs,
                "[]", owner, CLOCK.instant());
        order.setCompletion(null, null,
                "[{\"id\":\"dimensions\",\"label\":\"Dimensions checked\",\"checked\":false}]",
                null, CLOCK.instant());
        return order;
    }
}
