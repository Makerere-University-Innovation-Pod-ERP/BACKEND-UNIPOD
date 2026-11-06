package org.unipod.unipoderpbackend.submissions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SectorMemberRepository extends JpaRepository<SectorMember, UUID> {
    List<SectorMember> findAllBySectorIdOrderBySubjectAsc(UUID sectorId);

    boolean existsBySectorIdAndSubject(UUID sectorId, String subject);

    void deleteBySectorIdAndSubject(UUID sectorId, String subject);
}
