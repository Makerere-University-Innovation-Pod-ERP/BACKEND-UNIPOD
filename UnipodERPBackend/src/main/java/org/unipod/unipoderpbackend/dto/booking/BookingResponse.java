package org.unipod.unipoderpbackend.dto.booking;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;

public record BookingResponse(
        UUID id,
        String reference,
        UUID facilityId,
        String facilityName,
        String facilityLocation,
        String requesterId,
        String purpose,
        Instant startsAt,
        Instant endsAt,
        int attendees,
        String operator,
        BookingStatus status,
        String rejectionReason,
        String requestType,
        boolean isEquipmentRequest,
        String equipmentName,
        String purposeDetail,
        String decisionNote,
        Instant decidedAt,
        String conditionOut,
        String conditionReturned,
        Instant returnedAt,
        String returnNotes,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getReference(),
                booking.getFacility().getId(),
                booking.getFacility().getName(),
                booking.getFacility().getLocation(),
                booking.getRequesterId(),
                booking.getPurpose(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getAttendees(),
                booking.getOperator(),
                booking.getStatus(),
                booking.getRejectionReason(),
                booking.getRequestType(),
                booking.isEquipmentRequest(),
                booking.getEquipmentName(),
                booking.getPurposeDetail(),
                booking.getDecisionNote(),
                booking.getDecidedAt(),
                booking.getConditionOut(),
                booking.getConditionReturned(),
                booking.getReturnedAt(),
                booking.getReturnNotes(),
                booking.getDeliveredAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}

