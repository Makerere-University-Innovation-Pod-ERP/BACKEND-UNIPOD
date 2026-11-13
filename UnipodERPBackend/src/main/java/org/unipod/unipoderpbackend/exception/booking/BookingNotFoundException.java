package org.unipod.unipoderpbackend.exception.booking;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(UUID id) {
        super("Booking " + id + " was not found.");
    }
}
