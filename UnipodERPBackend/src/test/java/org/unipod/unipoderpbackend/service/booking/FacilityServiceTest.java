package org.unipod.unipoderpbackend.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.booking.CreateFacilityRequest;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

class FacilityServiceTest {
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final String ACTOR = "admin@example.org";

    private final FacilityRepository facilities = mock(FacilityRepository.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final FacilityService service = new FacilityService(
            facilities, auditTrail, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void defaultsCncFacilitiesToRequiringSafetyPass() {
        when(facilities.existsByNameIgnoreCase("CNC Lab")).thenReturn(false);
        when(facilities.saveAndFlush(any(Facility.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new CreateFacilityRequest("CNC Lab", null, 2, FacilityStatus.AVAILABLE, "CNC", null),
                ACTOR);

        assertTrue(response.requiresSafetyPass());
    }

    @Test
    void safetyPassPatchUpdatesTheFlagAndRecordsAudit() {
        Facility facility = facility(false);
        when(facilities.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));

        var response = service.updateSafetyPassRequirement(FACILITY_ID, true, ACTOR);

        assertTrue(response.requiresSafetyPass());
        assertEquals(NOW, response.updatedAt());
        verify(facilities).saveAndFlush(facility);
        verify(auditTrail).record(
                eq(ACTOR),
                eq("facility.safety_pass_requirement_changed"),
                eq("facility"),
                eq(FACILITY_ID),
                any(),
                any());
    }

    @Test
    void unchangedSafetyPassPatchDoesNotWriteOrAudit() {
        Facility facility = facility(false);
        when(facilities.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));

        var response = service.updateSafetyPassRequirement(FACILITY_ID, false, ACTOR);

        assertFalse(response.requiresSafetyPass());
        assertEquals(NOW, response.updatedAt());
        verify(facilities, never()).saveAndFlush(any(Facility.class));
        verify(auditTrail, never()).record(
                eq(ACTOR), eq("facility.safety_pass_requirement_changed"), eq("facility"), eq(FACILITY_ID),
                any(), any());
    }

    private static Facility facility(boolean requiresSafetyPass) {
        return new Facility(
                FACILITY_ID,
                "CNC Lab",
                null,
                2,
                FacilityStatus.AVAILABLE,
                "CNC",
                requiresSafetyPass,
                ACTOR,
                NOW
        );
    }
}
