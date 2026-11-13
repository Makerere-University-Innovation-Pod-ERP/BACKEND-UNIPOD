package org.unipod.unipoderpbackend.repository.review;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;

public interface ReviewSettingsRepository extends JpaRepository<ReviewSettings, Long> {
    Optional<ReviewSettings> findByBriefIdAndRound(UUID briefId, int round);

    Optional<ReviewSettings> findTopByBriefIdOrderByRoundDesc(UUID briefId);
}
