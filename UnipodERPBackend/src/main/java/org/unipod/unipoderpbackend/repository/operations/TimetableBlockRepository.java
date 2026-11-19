package org.unipod.unipoderpbackend.repository.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.TimetableBlock;

public interface TimetableBlockRepository extends JpaRepository<TimetableBlock, UUID> {
    @Query("""
            select b from TimetableBlock b
            where (:facilityId is null or b.facilityId = :facilityId)
              and b.startsAt < :to and b.endsAt > :from
            order by b.startsAt asc
            """)
    List<TimetableBlock> findOverlapping(@Param("facilityId") UUID facilityId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);
}

