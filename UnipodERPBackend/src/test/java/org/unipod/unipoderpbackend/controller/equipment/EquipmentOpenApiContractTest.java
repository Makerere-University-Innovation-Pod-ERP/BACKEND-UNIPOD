package org.unipod.unipoderpbackend.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.dto.equipment.ApproveLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.CreateEquipmentRequest;
import org.unipod.unipoderpbackend.dto.equipment.CreateMachineRequest;
import org.unipod.unipoderpbackend.dto.equipment.DeclineLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.EquipmentResponse;
import org.unipod.unipoderpbackend.dto.equipment.LoanResponse;
import org.unipod.unipoderpbackend.dto.equipment.MachineFaultReportResponse;
import org.unipod.unipoderpbackend.dto.equipment.MachineResponse;
import org.unipod.unipoderpbackend.dto.equipment.MarkEquipmentWorkingRequest;
import org.unipod.unipoderpbackend.dto.equipment.MarkMachineWorkingRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReportEquipmentFaultRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReportMachineRequest;
import org.unipod.unipoderpbackend.dto.equipment.RequestLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReturnLoanRequest;
import org.unipod.unipoderpbackend.dto.equipment.TelecomsEquipmentResponse;
import org.unipod.unipoderpbackend.dto.stock.CncStockMovementResponse;
import org.unipod.unipoderpbackend.dto.stock.CncStockResponse;
import org.unipod.unipoderpbackend.dto.stock.CreateStockItemRequest;
import org.unipod.unipoderpbackend.dto.stock.RecordCncMovementRequest;
import org.unipod.unipoderpbackend.dto.stock.RecordTelecomsMovementRequest;
import org.unipod.unipoderpbackend.dto.stock.RecordTextileMovementRequest;
import org.unipod.unipoderpbackend.dto.stock.StockItemResponse;
import org.unipod.unipoderpbackend.dto.stock.TelecomsStockMovementResponse;
import org.unipod.unipoderpbackend.dto.stock.TextileStockMovementResponse;
import org.unipod.unipoderpbackend.dto.stock.TextileStockResponse;
import org.unipod.unipoderpbackend.dto.stock.UpdateMinimumRequest;
import org.unipod.unipoderpbackend.model.equipment.EquipmentState;
import org.unipod.unipoderpbackend.model.equipment.FaultKind;
import org.unipod.unipoderpbackend.model.equipment.LoanStatus;
import org.unipod.unipoderpbackend.model.equipment.MachineState;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;
import org.yaml.snakeyaml.Yaml;

/** Keeps the documented equipment, machine and stock contract (openapi.yaml at the repository root) equal to the code. */
class EquipmentOpenApiContractTest {
    private static final Set<String> READ = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR",
            "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER");
    private static final Set<String> REPORT = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR",
            "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER");
    private static final Set<String> MANAGE = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR");
    private static final Set<String> TELECOMS_STOCK_READ = withExtraRole(READ, "TELECOMS_DOMAIN_OWNER");
    private static final Set<String> TELECOMS_STOCK_MANAGE = withExtraRole(MANAGE, "TELECOMS_DOMAIN_OWNER");
    private static final Set<String> READ_ERRORS = Set.of("200", "401", "403");
    private static final Set<String> MUTATION_ERRORS = Set.of("400", "401", "403", "404", "422");

    private static Map<String, Object> spec;
    private static String specText;

    @BeforeAll
    static void loadSpec() throws IOException {
        Path path = Path.of("..", "openapi.yaml");
        specText = Files.readString(path, StandardCharsets.UTF_8);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            spec = new Yaml().load(reader);
        }
    }

    @Test
    void documentsEveryOperationWithItsRolesAndErrors() {
        assertOperation("/operations/equipment", "get", "Equipment", "listMultimediaEquipment", READ, READ_ERRORS);
        assertOperation("/operations/equipment", "post", "Equipment", "registerMultimediaEquipment", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/operations/equipment/report-fault", "post", "Equipment", "reportMultimediaEquipmentFault", REPORT,
                withOk("200", MUTATION_ERRORS));
        assertOperation("/operations/equipment/{id}/mark-working", "post", "Equipment", "markMultimediaEquipmentWorking",
                MANAGE, Set.of("200", "400", "401", "403", "404", "409", "422"));
        assertOperation("/telecoms/equipment/{id}/mark-working", "post", "Equipment", "markTelecomsEquipmentWorking",
                MANAGE, Set.of("200", "400", "401", "403", "404", "409", "422"));
        assertOperation("/telecoms/equipment", "get", "Equipment", "listTelecomsEquipment", READ, READ_ERRORS);
        assertOperation("/telecoms/equipment", "post", "Equipment", "registerTelecomsEquipment", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/telecoms/equipment/report-fault", "post", "Equipment", "reportTelecomsEquipmentFault", REPORT,
                withOk("200", MUTATION_ERRORS));
        assertOperation("/telecoms/equipment/loans", "get", "Equipment loans", "listTelecomsLoans", READ, READ_ERRORS);
        assertOperation("/telecoms/equipment/loans", "post", "Equipment loans", "requestTelecomsLoan", REPORT,
                Set.of("201", "400", "401", "403", "404", "409", "422"));
        for (String action : List.of("approve", "decline", "return")) {
            assertOperation("/telecoms/equipment/loans/{id}/" + action, "post", "Equipment loans",
                    action + "TelecomsLoan", MANAGE, Set.of("200", "400", "401", "403", "404", "409", "422"));
        }
        assertOperation("/textile/machines", "get", "Machines", "listTextileMachines", READ, READ_ERRORS);
        assertOperation("/textile/machines", "post", "Machines", "registerTextileMachine", MANAGE,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/textile/machines/{id}/reports", "post", "Machines", "reportTextileMachine", REPORT,
                withOk("201", MUTATION_ERRORS));
        assertOperation("/textile/machines/{id}/mark-working", "post", "Machines", "markTextileMachineWorking", MANAGE,
                Set.of("200", "400", "401", "403", "404", "409", "422"));
        for (String department : List.of("cnc", "telecoms", "textile")) {
            String name = department.equals("cnc") ? "Cnc" : department.equals("telecoms") ? "Telecoms" : "Textile";
            String itemPath = department.equals("telecoms") ? "/telecoms/stock/{id}/minimum" : "/" + department + "/stock/{id}";
            Set<String> stockReadRoles = department.equals("telecoms") ? TELECOMS_STOCK_READ : READ;
            Set<String> stockManageRoles = department.equals("telecoms") ? TELECOMS_STOCK_MANAGE : MANAGE;
            assertOperation("/" + department + "/stock", "get", "Stock", "list" + name + "Stock", stockReadRoles, READ_ERRORS);
            assertOperation("/" + department + "/stock", "post", "Stock", "register" + name + "StockItem", stockManageRoles,
                    Set.of("201", "400", "401", "403", "409", "422"));
            assertOperation("/" + department + "/stock/movements", "get", "Stock", "list" + name + "StockMovements",
                    stockReadRoles, READ_ERRORS);
            assertOperation("/" + department + "/stock/movements", "post", "Stock", "record" + name + "StockMovement",
                    stockManageRoles, withOk("201", MUTATION_ERRORS));
            assertOperation(itemPath, "patch", "Stock", "update" + name + "StockMinimum", stockManageRoles,
                    withOk("200", MUTATION_ERRORS));
        }
    }

    @Test
    void everyReferenceInTheSpecResolves() {
        Matcher matcher = Pattern.compile("\\$ref: '#/components/(\\w+)/(\\w+)'").matcher(specText);
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            Map<String, Object> section = component(matcher.group(1));
            if (section == null || !section.containsKey(matcher.group(2))) {
                missing.add(matcher.group(1) + "/" + matcher.group(2));
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void enumsMatchTheCode() {
        assertEquals(names(EquipmentState.values()), enumValues("EquipmentState"));
        assertEquals(names(MachineState.values()), enumValues("MachineState"));
        assertEquals(names(FaultKind.values()), enumValues("FaultKind"));
        assertEquals(names(StockMovementType.values()), enumValues("StockMovementType"));
        // APPROVED is deliberately not a status the API sends (D-043).
        assertEquals(names(LoanStatus.values()), enumValues("LoanStatus"));
    }

    @Test
    void requestSchemasListExactlyTheFieldsTheRequestsAccept() {
        assertEquals(components(CreateEquipmentRequest.class), schemaProperties("CreateEquipmentRequest"));
        assertEquals(components(ReportEquipmentFaultRequest.class), schemaProperties("ReportEquipmentFaultRequest"));
        assertEquals(components(RequestLoanRequest.class), schemaProperties("RequestLoanRequest"));
        assertEquals(components(ApproveLoanRequest.class), schemaProperties("ApproveLoanRequest"));
        assertEquals(components(DeclineLoanRequest.class), schemaProperties("DeclineLoanRequest"));
        assertEquals(components(ReturnLoanRequest.class), schemaProperties("ReturnLoanRequest"));
        assertEquals(components(CreateMachineRequest.class), schemaProperties("CreateMachineRequest"));
        assertEquals(components(ReportMachineRequest.class), schemaProperties("ReportMachineRequest"));
        assertEquals(components(MarkMachineWorkingRequest.class), schemaProperties("MarkMachineWorkingRequest"));
        assertEquals(components(MarkEquipmentWorkingRequest.class), schemaProperties("MarkEquipmentWorkingRequest"));
        assertEquals(components(CreateStockItemRequest.class), schemaProperties("CreateStockItemRequest"));
        assertEquals(components(RecordCncMovementRequest.class), schemaProperties("RecordCncStockMovementRequest"));
        assertEquals(components(RecordTelecomsMovementRequest.class), schemaProperties("RecordTelecomsStockMovementRequest"));
        assertEquals(components(RecordTextileMovementRequest.class), schemaProperties("RecordTextileStockMovementRequest"));
        assertEquals(components(UpdateMinimumRequest.class), schemaProperties("UpdateStockMinimumRequest"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestSchemasRequireExactlyTheFieldsTheRequestsRequire() {
        Map<String, Class<? extends Record>> requests = new java.util.LinkedHashMap<>();
        requests.put("CreateEquipmentRequest", CreateEquipmentRequest.class);
        requests.put("ReportEquipmentFaultRequest", ReportEquipmentFaultRequest.class);
        requests.put("RequestLoanRequest", RequestLoanRequest.class);
        requests.put("ApproveLoanRequest", ApproveLoanRequest.class);
        requests.put("DeclineLoanRequest", DeclineLoanRequest.class);
        requests.put("ReturnLoanRequest", ReturnLoanRequest.class);
        requests.put("CreateMachineRequest", CreateMachineRequest.class);
        requests.put("ReportMachineRequest", ReportMachineRequest.class);
        requests.put("MarkMachineWorkingRequest", MarkMachineWorkingRequest.class);
        requests.put("MarkEquipmentWorkingRequest", MarkEquipmentWorkingRequest.class);
        requests.put("CreateStockItemRequest", CreateStockItemRequest.class);
        requests.put("RecordCncStockMovementRequest", RecordCncMovementRequest.class);
        requests.put("RecordTelecomsStockMovementRequest", RecordTelecomsMovementRequest.class);
        requests.put("RecordTextileStockMovementRequest", RecordTextileMovementRequest.class);
        requests.put("UpdateStockMinimumRequest", UpdateMinimumRequest.class);

        requests.forEach((schemaName, type) -> {
            Set<String> required = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                    .filter(name -> isRequired(type, name)).collect(Collectors.toCollection(TreeSet::new));
            Object documented = schema(schemaName).get("required");
            Set<String> documentedRequired = documented == null ? new TreeSet<>() : new TreeSet<>((Collection<String>) documented);
            assertEquals(required, documentedRequired, schemaName);
        });
    }

    private static boolean isRequired(Class<? extends Record> type, String field) {
        try {
            var declared = type.getDeclaredField(field);
            return declared.isAnnotationPresent(NotNull.class) || declared.isAnnotationPresent(NotBlank.class);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void thePhotoOnAMachineReportIsDocumentedAsItIsSent() {
        Map<String, Object> report = (Map<String, Object>) ((Map<String, Object>) schema("MachineFaultReportResult")
                .get("properties")).get("report");
        Map<String, Object> photo = (Map<String, Object>) ((Map<String, Object>) report.get("properties")).get("photo");
        List<Map<String, Object>> alternatives = (List<Map<String, Object>>) photo.get("oneOf");
        Map<String, Object> object = alternatives.stream().filter(a -> "object".equals(a.get("type"))).findFirst().orElseThrow();

        assertEquals(components(MachineFaultReportResponse.Photo.class),
                new TreeSet<>(((Map<String, Object>) object.get("properties")).keySet()));
        assertEquals(components(MachineFaultReportResponse.Photo.class), new TreeSet<>((Collection<String>) object.get("required")));
        assertTrue(alternatives.stream().anyMatch(a -> "null".equals(a.get("type"))), "photo may be null");
    }

    @Test
    void responseSchemasListExactlyTheFieldsTheApiReturns() {
        assertEquals(components(EquipmentResponse.class), schemaProperties("EquipmentItem"));
        assertEquals(components(TelecomsEquipmentResponse.class), schemaProperties("TelecomsEquipmentItem"));
        assertEquals(components(LoanResponse.class), schemaProperties("EquipmentLoan"));
        assertEquals(components(LoanResponse.ManagerApproval.class), schemaProperties("LoanManagerApproval"));
        assertEquals(components(MachineResponse.class), schemaProperties("Machine"));
        assertEquals(components(MachineFaultReportResponse.class), schemaProperties("MachineFaultReportResult"));
        assertEquals(components(MachineFaultReportResponse.Report.class), reportProperties());
        assertEquals(components(StockItemResponse.class), schemaProperties("StockItem"));
        assertEquals(components(CncStockMovementResponse.class), schemaProperties("CncStockMovement"));
        assertEquals(components(CncStockResponse.class), schemaProperties("CncStockList"));
        assertEquals(components(TelecomsStockMovementResponse.class), schemaProperties("TelecomsStockMovement"));
        assertEquals(components(TextileStockMovementResponse.class), schemaProperties("TextileStockMovement"));
        assertEquals(components(TextileStockResponse.class), schemaProperties("TextileStockList"));
    }

    @Test
    void requiredFieldsOfEveryResponseSchemaAreAllOfItsFields() {
        for (String name : List.of("EquipmentItem", "TelecomsEquipmentItem", "EquipmentLoan", "LoanManagerApproval",
                "Machine", "MachineFaultReportResult", "StockItem", "CncStockMovement", "CncStockList",
                "TelecomsStockMovement", "TextileStockMovement", "TextileStockList")) {
            assertEquals(schemaProperties(name), schemaRequired(name), name);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> reportProperties() {
        Map<String, Object> result = schema("MachineFaultReportResult");
        Map<String, Object> report = (Map<String, Object>) ((Map<String, Object>) result.get("properties")).get("report");
        return new TreeSet<>(((Map<String, Object>) report.get("properties")).keySet());
    }

    private static Set<String> withExtraRole(Set<String> roles, String extraRole) {
        Set<String> all = new TreeSet<>(roles);
        all.add(extraRole);
        return all;
    }

    private static Set<String> withOk(String ok, Set<String> errors) {
        Set<String> all = new TreeSet<>(errors);
        all.add(ok);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static void assertOperation(String path, String method, String tag, String operationId, Set<String> roles,
                                        Set<String> responses) {
        Map<String, Object> pathItem = (Map<String, Object>) ((Map<String, Object>) spec.get("paths")).get(path);
        assertTrue(pathItem != null, path + " is not documented");
        Map<String, Object> operation = (Map<String, Object>) pathItem.get(method);
        assertTrue(operation != null, path + " " + method + " is not documented");
        assertEquals(operationId, operation.get("operationId"), path + " " + method);
        assertEquals(List.of(tag), operation.get("tags"), path + " " + method);
        assertEquals(roles, Set.copyOf((List<String>) operation.get("x-required-roles")), path + " " + method);
        assertEquals(new TreeSet<>(responses), new TreeSet<>(((Map<String, Object>) operation.get("responses")).keySet()),
                path + " " + method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> component(String kind) {
        return (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get(kind);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        Map<String, Object> found = (Map<String, Object>) component("schemas").get(name);
        assertTrue(found != null, "schema " + name + " is not documented");
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> schemaProperties(String name) {
        return new TreeSet<>(((Map<String, Object>) schema(name).get("properties")).keySet());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> schemaRequired(String name) {
        return new TreeSet<>((Collection<String>) schema(name).get("required"));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> enumValues(String name) {
        return new TreeSet<>((List<String>) schema(name).get("enum"));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
