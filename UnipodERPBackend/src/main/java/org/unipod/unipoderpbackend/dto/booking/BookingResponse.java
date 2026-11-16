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
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    public static BookingResponse forSubmitter(Booking booking) {
        BookingResponse response = from(booking);
        return new BookingResponse(
                response.id(), response.reference(), response.facilityId(), response.facilityName(),
                response.facilityLocation(), response.requesterId(), response.purpose(), response.startsAt(),
                response.endsAt(), response.attendees(), response.operator(), response.status(),
                response.rejectionReason(), response.requestType(), response.isEquipmentRequest(),
                response.equipmentName(), response.purposeDetail(), null, null, null, null, null, null,
                response.createdAt(), response.updatedAt()
        );
    }
}

