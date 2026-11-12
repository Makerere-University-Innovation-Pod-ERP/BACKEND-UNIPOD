package org.unipod.unipoderpbackend.repository.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
    @Query("""
            select entry
            from AuditEntry entry
            where (coalesce(:actor, '') = '' or entry.actor = :actor)
              and (coalesce(:action, '') = '' or entry.action = :action)
              and (coalesce(:entityType, '') = '' or entry.entityType = :entityType)
              and (:entityId is null or entry.entityId = :entityId)
              and entry.occurredAt >= :from
              and entry.occurredAt < :to
            order by entry.occurredAt desc, entry.id desc
            """)
    Page<AuditEntry> search(
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    Page<AuditEntry> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
            String entityType,
            UUID entityId,
            Pageable pageable
    );
}
