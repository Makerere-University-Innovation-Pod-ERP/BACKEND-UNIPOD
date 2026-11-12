package org.unipod.unipoderpbackend.controller.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.partner.CreatePartnerRequest;
import org.unipod.unipoderpbackend.dto.partner.RecordMouRenewalRequest;
import org.unipod.unipoderpbackend.dto.partner.UpdatePartnerRequest;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.yaml.snakeyaml.Yaml;

/** Keeps the documented partner contract (openapi.yaml at the repository root) equal to the code. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerOpenApiContractTest {
    private static final Set<String> READ_ROLES = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR",
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER",
            "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER");
    private static final Set<String> MANAGE_ROLES = Set.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR");

    private static Map<String, Object> spec;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void loadSpec() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("..", "openapi.yaml"), StandardCharsets.UTF_8)) {
            spec = new Yaml().load(reader);
        }
    }

    @Test
    void documentsEveryPartnerOperationWithItsRolesAndErrors() {
        assertOperation("/operations/partners", "get", "listPartners", READ_ROLES, Set.of("200", "401", "403"));
        assertOperation("/operations/partners", "post", "createPartner", MANAGE_ROLES,
                Set.of("201", "400", "401", "403", "409", "422"));
        assertOperation("/operations/partners/overview", "get", "getPartnersOverview", READ_ROLES,
                Set.of("200", "401", "403"));
        assertOperation("/operations/partners/{id}", "patch", "updatePartner", MANAGE_ROLES,
                Set.of("200", "400", "401", "403", "404", "409", "422"));
        assertOperation("/operations/partners/{id}/mou", "put", "recordPartnerMouRenewal", MANAGE_ROLES,
                Set.of("200", "400", "401", "403", "404", "409", "422"));
    }

    @Test
    void enumsMatchTheCode() {
        assertEquals(names(PartnerType.values()), enumValues("PartnerType"));
        assertEquals(names(PartnerStatus.values()), enumValues("PartnerStatus"));
    }

    @Test
    void requestSchemasListExactlyTheFieldsTheRequestsAccept() {
        assertEquals(components(CreatePartnerRequest.class), schemaProperties("CreatePartnerRequest"));
        assertEquals(components(UpdatePartnerRequest.class), schemaProperties("UpdatePartnerRequest"));
        assertEquals(components(RecordMouRenewalRequest.class), schemaProperties("RecordMouRenewalRequest"));
    }

    @Test
    void responseSchemasListExactlyTheFieldsTheApiReturns() throws Exception {
        RequestPostProcessor admin = user("contract-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR");
        String created = body(MockMvcRequestBuilders.post("/operations/partners").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Contract " + UUID.randomUUID() + "\", \"type\": \"FUNDER\"}"), 201);
        String id = JsonPath.read(created, "$.id");
        Set<String> partner = schemaProperties("Partner");

        assertEquals(partner, keys(created, "$"));
        assertEquals(schemaRequired("Partner"), partner);
        assertEquals(partner, keys(body(MockMvcRequestBuilders.get("/operations/partners").with(admin), 200),
                "$[0]"));
        String overview = body(MockMvcRequestBuilders.get("/operations/partners/overview").with(admin), 200);
        assertEquals(schemaProperties("PartnersOverview"), keys(overview, "$"));
        assertEquals(partner, keys(overview, "$.partners[0]"));
        assertEquals(partner, keys(body(MockMvcRequestBuilders.patch("/operations/partners/{id}", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0, \"notes\": \"n\"}"), 200), "$"));
        assertEquals(partner, keys(body(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"renewalDate\": \"2030-01-01\"}"), 200), "$"));
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus)
            throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString();
    }

    private static Set<String> keys(String json, String path) {
        Map<String, Object> object = JsonPath.read(json, path);
        return new TreeSet<>(object.keySet());
    }

    @SuppressWarnings("unchecked")
    private static void assertOperation(
            String path, String method, String operationId, Set<String> roles, Set<String> responses) {
        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                spec.get("paths")).get(path)).get(method);
        assertEquals(operationId, operation.get("operationId"), path + " " + method);
        assertEquals(List.of("Partners"), operation.get("tags"), path + " " + method);
        assertEquals(roles, Set.copyOf((List<String>) operation.get("x-required-roles")), path + " " + method);
        assertEquals(responses, ((Map<String, Object>) operation.get("responses")).keySet(), path + " " + method);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) spec.get("components"))
                .get("schemas")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> schemaProperties(String name) {
        return new TreeSet<>(((Map<String, Object>) schema(name).get("properties")).keySet());
    }

    @SuppressWarnings("unchecked")
    private static Set<String> schemaRequired(String name) {
        return new TreeSet<>((List<String>) schema(name).get("required"));
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
