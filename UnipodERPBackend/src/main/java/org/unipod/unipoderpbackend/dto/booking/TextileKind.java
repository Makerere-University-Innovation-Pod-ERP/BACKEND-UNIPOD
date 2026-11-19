package org.unipod.unipoderpbackend.dto.booking;

import org.unipod.unipoderpbackend.model.booking.BookingKind;

/** The two kinds of textile booking the workshop screen offers. */
public enum TextileKind {
    WORKSHOP,
    TRAINING;

    public BookingKind toBookingKind() {
        return this == WORKSHOP ? BookingKind.WORKSHOP : BookingKind.TRAINING;
    }

    public static TextileKind from(BookingKind kind) {
        return kind == BookingKind.TRAINING ? TRAINING : WORKSHOP;
    }
}
