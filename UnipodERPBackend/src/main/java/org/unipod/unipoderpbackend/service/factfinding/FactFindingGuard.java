package org.unipod.unipoderpbackend.service.factfinding;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;

@Service
public class FactFindingGuard {
    private final FactFindingRepository checks;

    public FactFindingGuard(FactFindingRepository checks) {
        this.checks = checks;
    }

    public boolean isComplete(UUID briefId) {
        return checks.existsByBriefIdAndCompletedAtIsNotNull(briefId);
    }
}
