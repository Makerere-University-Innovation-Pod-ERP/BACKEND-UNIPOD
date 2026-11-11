package org.unipod.unipoderpbackend.service.factfinding;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactFindingGuardTest {
    private final FactFindingRepository checks = mock(FactFindingRepository.class);
    private final FactFindingGuard guard = new FactFindingGuard(checks);

    @Test
    void reportsCompleteWhenACompletedCheckExists() {
        UUID briefId = UUID.randomUUID();
        when(checks.existsByBriefIdAndCompletedAtIsNotNull(briefId)).thenReturn(true);

        assertTrue(guard.isComplete(briefId));
    }

    @Test
    void reportsIncompleteWhenNoCompletedCheckExists() {
        UUID briefId = UUID.randomUUID();
        when(checks.existsByBriefIdAndCompletedAtIsNotNull(briefId)).thenReturn(false);

        assertFalse(guard.isComplete(briefId));
    }
}
