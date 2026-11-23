package org.unipod.unipoderpbackend.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import tools.jackson.databind.ObjectMapper;

class AdminUpdateUserRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void distinguishesAnOmittedDepartmentFromExplicitNull() throws Exception {
        AdminUpdateUserRequest omitted = objectMapper.readValue(
                "{\"role\":\"REVIEWER\",\"version\":0}",
                AdminUpdateUserRequest.class
        );
        AdminUpdateUserRequest cleared = objectMapper.readValue(
                "{\"role\":\"REVIEWER\",\"departmentId\":null,\"version\":0}",
                AdminUpdateUserRequest.class
        );

        assertFalse(omitted.departmentIdProvided());
        assertTrue(cleared.departmentIdProvided());
        assertNull(cleared.departmentId());
        assertEquals(ApiUserRole.REVIEWER, omitted.role());
    }
}
