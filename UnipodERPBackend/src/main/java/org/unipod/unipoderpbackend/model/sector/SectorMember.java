package org.unipod.unipoderpbackend.model.sector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sector_members")
public class SectorMember {
    @Id
    private UUID id;

    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;

    @Column(nullable = false, length = 255)
    private String subject;

    protected SectorMember() {
    }

    public SectorMember(UUID id, UUID sectorId, String subject) {
        this.id = id;
        this.sectorId = sectorId;
        this.subject = subject;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public String getSubject() {
        return subject;
    }
}
