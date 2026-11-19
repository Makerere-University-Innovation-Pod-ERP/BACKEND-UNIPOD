package org.unipod.unipoderpbackend.controller.training;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.support.ZodShape;

/** Sessions, registrations and the frontend's bootcamp schema for the bootcamp tests. */
final class BootcampTestData {
    // The frontend's bootcampSessionSchema (services/telecoms.js).
    static final ZodShape PARTICIPANT_SHAPE = ZodShape.object()
            .string("id").string("name").string("email").nullableString("studentNumber")
            .enumOf("paymentStatus", "NOT_REQUESTED", "AWAITING_PROOF", "PROOF_SUBMITTED", "CONFIRMED", "REJECTED")
            .string("enrolledAt");
    static final ZodShape ATTENDANCE_SHAPE = ZodShape.object()
            .string("participantId").enumOf("status", "PRESENT", "ABSENT");
    static final ZodShape BOOTCAMP_SHAPE = ZodShape.object()
            .string("id").string("topic").string("instructor").string("date").string("start").string("end")
            .number("capacity").number("fee")
            .arrayOf("enrolled", PARTICIPANT_SHAPE).arrayOf("attendance", ATTENDANCE_SHAPE);
    static final ZodShape LIST_SHAPE = ZodShape.object().arrayOf("items", BOOTCAMP_SHAPE);

    static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };
    static final String[] READ_ROLES = {
            "ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
            "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER"
    };
    static final Instant SEEDED_AT = Instant.parse("2026-11-12T05:00:00Z");

    private BootcampTestData() {
    }

    static TrainingSession session(UUID department, Instant startsAt, int capacity, int fee) {
        return new TrainingSession(UUID.randomUUID(), department, "Bootcamp " + UUID.randomUUID(), "Eng. Kaddu",
                startsAt, startsAt.plusSeconds(7 * 3600), capacity, "Telecoms Lab", "", fee, false, true, SEEDED_AT);
    }

    static TrainingParticipant participant(UUID session, String regNumber, String name, String email, int fee) {
        return new TrainingParticipant(UUID.randomUUID(), session, name, regNumber, "Not given", email, fee, SEEDED_AT);
    }
}
