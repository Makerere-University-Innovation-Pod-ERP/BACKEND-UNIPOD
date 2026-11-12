package org.unipod.unipoderpbackend.service.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.audit.ActivityFilter;
import org.unipod.unipoderpbackend.exception.authorization.AccessDeniedWithReasonException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.DenialCode;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationPolicy;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T08:30:00Z");

    private final AuditEntryRepository entries = mock(AuditEntryRepository.class);
    private final AuditService audit = new AuditService(
            entries,
            mock(SubmissionRepository.class),
            new AuthorizationService(new AuthorizationPolicy()),
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneId.of("Africa/Kampala"))
    );

    @Test
    void logWritesWhoDidWhatAndWhen() {
        Actor admin = new Actor("Admin@Unipod.ac.ug", ApiUserRole.ADMINISTRATOR, Set.of());
        UUID userId = UUID.randomUUID();

        audit.log(AuditEvent.changed(admin, "user.role_changed", "user", userId,
                Map.of("role", "REVIEWER"), Map.of("role", "OBSERVER")));

        ArgumentCaptor<AuditEntry> saved = ArgumentCaptor.forClass(AuditEntry.class);
        verify(entries).save(saved.capture());
        AuditEntry entry = saved.getValue();
        assertEquals(NOW, entry.getOccurredAt());
        assertEquals("admin@unipod.ac.ug", entry.getActor());
        assertEquals("ADMINISTRATOR", entry.getActorRole());
        assertEquals("user.role_changed", entry.getAction());
        assertEquals("user", entry.getEntityType());
        assertEquals(userId, entry.getEntityId());
        assertEquals("{\"role\":\"REVIEWER\"}", entry.getBeforeValue());
        assertEquals("{\"role\":\"OBSERVER\"}", entry.getAfterValue());
        assertNull(entry.getRequestId());
    }

    @Test
    void createdEventHasNoBeforeValue() {
        Actor admin = new Actor("admin@unipod.ac.ug", ApiUserRole.ADMINISTRATOR, Set.of());

        audit.log(AuditEvent.created(admin, "user.created", "user", UUID.randomUUID(), Map.of("email", "x@y.z")));

        ArgumentCaptor<AuditEntry> saved = ArgumentCaptor.forClass(AuditEntry.class);
        verify(entries).save(saved.capture());
        assertNull(saved.getValue().getBeforeValue());
    }

    @Test
    void actionNamesMustBeLowerCaseWithDots() {
        Actor admin = new Actor("admin@unipod.ac.ug", ApiUserRole.ADMINISTRATOR, Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.created(admin, "User Created", "user", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> AuditEvent.created(admin, "user.created", "", null, null));
    }

    @Test
    void searchIsRefusedWithACodeForRolesWithoutAuditView() {
        Actor submitter = new Actor("student@mak.ac.ug", ApiUserRole.SUBMITTER, Set.of());

        AccessDeniedWithReasonException refused = assertThrows(AccessDeniedWithReasonException.class,
                () -> audit.search(submitter, emptyFilter(), 0, 25));

        assertEquals(DenialCode.ROLE_NOT_PERMITTED, refused.getDecision().code());
        verifyNoInteractions(entries);
    }

    @Test
    void searchRejectsFromAfterTo() {
        Actor admin = new Actor("admin@unipod.ac.ug", ApiUserRole.ADMINISTRATOR, Set.of());
        ActivityFilter backwards = new ActivityFilter(null, null, null, null,
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 1));

        assertThrows(ResponseStatusException.class, () -> audit.search(admin, backwards, 0, 25));
        verifyNoInteractions(entries);
    }

    private static ActivityFilter emptyFilter() {
        return new ActivityFilter(null, null, null, null, null, null);
    }
}
