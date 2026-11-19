package org.unipod.unipoderpbackend.dto.training;

import java.util.Optional;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;

/** The two attendance values the bootcamp roster knows; training stores two more (D-045). */
public enum BootcampAttendance {
    PRESENT,
    ABSENT;

    public AttendanceStatus toStored() {
        return this == PRESENT ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }

    /** UNMARKED has no entry on the roster; EXCUSED counts as absent. */
    public static Optional<BootcampAttendance> shown(AttendanceStatus stored) {
        return switch (stored) {
            case PRESENT -> Optional.of(PRESENT);
            case ABSENT, EXCUSED -> Optional.of(ABSENT);
            case UNMARKED -> Optional.empty();
        };
    }
}
