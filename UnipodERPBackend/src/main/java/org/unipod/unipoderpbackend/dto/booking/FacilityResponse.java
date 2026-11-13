package org.unipod.unipoderpbackend.dto.booking;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;

public record FacilityResponse(
        UUID id,
        String name,
        String location,
        int capacity,
        FacilityStatus status,
        String department,
        Instant createdAt,
        Instant updatedAt
) {
    public static FacilityResponse from(Facility facility) {
        return new FacilityResponse(
                facility.getId(),
                facility.getName(),
                facility.getLocation(),
                facility.getCapacity(),
                facility.getStatus(),
                facility.getDepartment(),
                facility.getCreatedAt(),
                facility.getUpdatedAt()
        );
    }
}
