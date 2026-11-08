package org.unipod.unipoderpbackend.service.sector;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.sector.SectorMemberRequest;
import org.unipod.unipoderpbackend.exception.sector.SectorNotFoundException;
import org.unipod.unipoderpbackend.model.sector.SectorMember;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;

@Service
public class SectorMemberService {
    private final SectorRepository sectors;
    private final SectorMemberRepository members;

    public SectorMemberService(SectorRepository sectors, SectorMemberRepository members) {
        this.sectors = sectors;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<String> list(UUID sectorId) {
        ensureSectorExists(sectorId);
        return members.findAllBySectorIdOrderBySubjectAsc(sectorId).stream()
                .map(SectorMember::getSubject)
                .toList();
    }

    @Transactional
    public String add(UUID sectorId, SectorMemberRequest request) {
        ensureSectorExists(sectorId);
        String subject = request.subject().trim();
        members.insertIfAbsent(UUID.randomUUID(), sectorId, subject);
        return subject;
    }

    @Transactional
    public void remove(UUID sectorId, String subject) {
        ensureSectorExists(sectorId);
        members.deleteBySectorIdAndSubject(sectorId, subject.trim());
    }

    private void ensureSectorExists(UUID sectorId) {
        if (!sectors.existsById(sectorId)) {
            throw new SectorNotFoundException(sectorId);
        }
    }
}
