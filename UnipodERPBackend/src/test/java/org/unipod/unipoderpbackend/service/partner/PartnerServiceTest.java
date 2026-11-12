package org.unipod.unipoderpbackend.service.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.unipod.unipoderpbackend.dto.partner.CreatePartnerRequest;
import org.unipod.unipoderpbackend.dto.partner.RecordMouRenewalRequest;
import org.unipod.unipoderpbackend.dto.partner.UpdatePartnerRequest;
import org.unipod.unipoderpbackend.exception.partner.PartnerConflictException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

/** Rules that need a failing database to show: which integrity errors count as "name taken". */
class PartnerServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T07:00:00Z");
    private static final Actor ACTOR = new Actor("admin@test.local", ApiUserRole.ADMINISTRATOR, Set.of());

    private final PartnerRepository repository = mock(PartnerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Africa/Kampala"));
    private final PartnerService service = new PartnerService(
            repository,
            new PartnerStatusPolicy(new PartnerMouProperties(60, 30, Set.of(), Set.of()), clock),
            audit,
            clock);

    @Test
    void creatingMapsAViolationOfTheNameIndexToAConflict() {
        when(repository.saveAndFlush(any())).thenThrow(nameIndexViolation());

        assertThrows(PartnerConflictException.class, () -> service.create(ACTOR, createRequest()));
        verify(audit, never()).log(any(AuditEvent.class));
    }

    @Test
    void creatingDoesNotCallOtherIntegrityErrorsADuplicateName() {
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement", new SQLException("value too long for type character varying(255)"));
        when(repository.saveAndFlush(any())).thenThrow(other);

        DataIntegrityViolationException thrown =
                assertThrows(DataIntegrityViolationException.class, () -> service.create(ACTOR, createRequest()));

        assertSame(other, thrown);
        verify(audit, never()).log(any(AuditEvent.class));
    }

    @Test
    void updatingMapsAViolationOfTheNameIndexToAConflict() {
        Partner partner = existingPartner();
        when(repository.saveAndFlush(any())).thenThrow(nameIndexViolation());

        assertThrows(PartnerConflictException.class,
                () -> service.update(ACTOR, partner.getId(), renameRequest("Renamed Ltd")));
    }

    @Test
    void updatingDoesNotCallOtherIntegrityErrorsADuplicateName() {
        Partner partner = existingPartner();
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement", new SQLException("invalid byte sequence for encoding UTF8"));
        when(repository.saveAndFlush(any())).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.update(ACTOR, partner.getId(), renameRequest("Renamed Ltd")));

        assertSame(other, thrown);
    }

    @Test
    void updatingWithNothingNewWritesNothingAndKeepsTheVersion() {
        Partner partner = existingPartner();

        var response = service.update(ACTOR, partner.getId(),
                new UpdatePartnerRequest(0L, "Existing Ltd", null, null, null, null, null));

        assertEquals(0L, response.version());
        verify(repository, never()).saveAndFlush(any());
        verify(audit, never()).log(any(AuditEvent.class));
    }

    @Test
    void renewingMapsNoIntegrityErrorToADuplicateName() {
        Partner partner = existingPartner();
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement", new SQLException("date out of range"));
        when(repository.saveAndFlush(any())).thenThrow(other);

        assertSame(other, assertThrows(DataIntegrityViolationException.class, () -> service.recordMouRenewal(
                ACTOR, partner.getId(), new RecordMouRenewalRequest(java.time.LocalDate.of(2030, 1, 1), null))));
    }

    private Partner existingPartner() {
        Partner partner = new Partner(UUID.randomUUID(), "Existing Ltd", PartnerType.FUNDER, null, null, null, null, NOW);
        ReflectionTestUtils.setField(partner, "version", 0L);
        when(repository.findById(partner.getId())).thenReturn(Optional.of(partner));
        return partner;
    }

    private static DataIntegrityViolationException nameIndexViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint uq_partners_name"));
    }

    private static CreatePartnerRequest createRequest() {
        return new CreatePartnerRequest("New Ltd", PartnerType.FUNDER, null, null, null, null);
    }

    private static UpdatePartnerRequest renameRequest(String name) {
        return new UpdatePartnerRequest(0L, name, null, null, null, null, null);
    }
}
