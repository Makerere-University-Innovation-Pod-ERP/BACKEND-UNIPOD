package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machine_booking_eligibilities")
public class MachineBookingEligibility {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, length = 254)
    private String userId;
    @Column(nullable = false, length = 32)
    private String department = "CNC";
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "granted_by", nullable = false, length = 254)
    private String grantedBy;

    protected MachineBookingEligibility() {}
    public MachineBookingEligibility(UUID id, String userId, String department, UUID sessionId, Instant grantedAt, String grantedBy) {
        this.id = id; this.userId = userId; this.department = department == null ? "CNC" : department;
        this.sessionId = sessionId; this.grantedAt = grantedAt; this.grantedBy = grantedBy;
    }
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getDepartment() { return department; }
}

