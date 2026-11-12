package org.unipod.unipoderpbackend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentActorTest {
    @Test
    void readsEmailAndRoleFromTheLogin() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("Admin@Unipod.ac.ug", null, "ROLE_ADMINISTRATOR");

        Actor actor = CurrentActor.from(login);

        assertEquals("admin@unipod.ac.ug", actor.email());
        assertEquals(ApiUserRole.ADMINISTRATOR, actor.role());
        assertTrue(actor.departmentIds().isEmpty());
    }

    @Test
    void rejectsUnknownRole() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("x@unipod.ac.ug", null, "ROLE_HACKER");

        assertThrows(AccessDeniedException.class, () -> CurrentActor.from(login));
    }

    @Test
    void rejectsLoginWithoutRole() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("x@unipod.ac.ug", null);

        assertThrows(AccessDeniedException.class, () -> CurrentActor.from(login));
    }

    @Test
    void rejectsMissingLogin() {
        assertThrows(AccessDeniedException.class, () -> CurrentActor.from(null));
    }
}
