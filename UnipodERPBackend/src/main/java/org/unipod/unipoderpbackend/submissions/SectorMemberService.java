package org.unipod.unipoderpbackend.submissions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        if (!members.existsBySectorIdAndSubject(sectorId, subject)) {
            members.save(new SectorMember(UUID.randomUUID(), sectorId, subject));
        }
        return subject;
    }

    @Transactional
    public void remove(UUID sectorId, String subject) {
        ensureSectorExists(sectorId);
        members.deleteBySectorIdAndSubject(sectorId, subject);
    }

    private void ensureSectorExists(UUID sectorId) {
        if (!sectors.existsById(sectorId)) {
            throw new SectorNotFoundException(sectorId);
        }
    }
}
