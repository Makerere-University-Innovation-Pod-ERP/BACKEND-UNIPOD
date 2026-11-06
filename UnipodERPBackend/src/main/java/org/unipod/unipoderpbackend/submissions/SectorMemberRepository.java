package org.unipod.unipoderpbackend.submissions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SectorMemberRepository extends JpaRepository<SectorMember, UUID> {
    List<SectorMember> findAllBySectorIdOrderBySubjectAsc(UUID sectorId);

    boolean existsBySectorIdAndSubject(UUID sectorId, String subject);

    void deleteBySectorIdAndSubject(UUID sectorId, String subject);

    @Modifying
    @Query(value = """
            INSERT INTO sector_members (id, sector_id, subject)
            VALUES (:id, :sectorId, :subject)
            ON CONFLICT (sector_id, subject) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("sectorId") UUID sectorId,
            @Param("subject") String subject
    );
}
