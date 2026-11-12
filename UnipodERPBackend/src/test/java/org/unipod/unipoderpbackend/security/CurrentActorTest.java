package org.unipod.unipoderpbackend.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentActorTest {
    private final SectorMemberRepository sectorMembers = mock(SectorMemberRepository.class);
    private final CurrentActor currentActor = new CurrentActor(sectorMembers);

    @Test
    void readsEmailAndRoleFromTheLogin() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("Admin@Unipod.ac.ug", null, "ROLE_ADMINISTRATOR");

        Actor actor = currentActor.from(login);

        assertEquals("admin@unipod.ac.ug", actor.email());
        assertEquals(ApiUserRole.ADMINISTRATOR, actor.role());
        assertTrue(actor.departmentIds().isEmpty());
    }

    @Test
    void departmentsComeFromSectorMembership() {
        UUID innovation = UUID.randomUUID();
        when(sectorMembers.findSectorIdsBySubject("officer@unipod.ac.ug")).thenReturn(Set.of(innovation));
        TestingAuthenticationToken login = new TestingAuthenticationToken("officer@unipod.ac.ug", null, "ROLE_SECTOR_OFFICER");

        Actor actor = currentActor.from(login);

        assertEquals(Set.of(innovation), actor.departmentIds());
    }

    @Test
    void rejectsUnknownRole() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("x@unipod.ac.ug", null, "ROLE_HACKER");

        assertThrows(AccessDeniedException.class, () -> currentActor.from(login));
    }

    @Test
    void rejectsLoginWithoutRole() {
        TestingAuthenticationToken login = new TestingAuthenticationToken("x@unipod.ac.ug", null, List.<GrantedAuthority>of());

        assertTrue(login.isAuthenticated());
        assertThrows(AccessDeniedException.class, () -> currentActor.from(login));
    }

    @Test
    void rejectsMissingLogin() {
        assertThrows(AccessDeniedException.class, () -> currentActor.from(null));
    }
}
