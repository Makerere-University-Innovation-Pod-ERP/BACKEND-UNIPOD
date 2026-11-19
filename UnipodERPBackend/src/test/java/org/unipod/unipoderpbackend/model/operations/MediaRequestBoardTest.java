package org.unipod.unipoderpbackend.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.unipod.unipoderpbackend.exception.media.MediaConflictException;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;

class MediaRequestBoardTest {
    private static final Instant T0 = Instant.parse("2026-11-12T05:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    @Test
    void movesOneStepAtATimeFromRequestedToDelivered() {
        MediaRequest request = requestIn("REQUESTED");

        request.moveTo(MediaBoardStatus.SCHEDULED, "coord@test.local", T1);
        assertEquals("SCHEDULED", request.getStatus());
        request.moveTo(MediaBoardStatus.IN_PRODUCTION, "coord@test.local", T1);
        assertEquals("IN_PRODUCTION", request.getStatus());
        assertNull(request.getDeliveredAt());

        request.moveTo(MediaBoardStatus.DELIVERED, "coord@test.local", T1);
        assertEquals("DELIVERED", request.getStatus());
        assertEquals(T1, request.getDeliveredAt());
        assertEquals("coord@test.local", request.getDeliveredBy());
        assertEquals(T1, request.getUpdatedAt());
    }

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, IN_PRODUCTION",
            "REQUESTED, DELIVERED",
            "REQUESTED, REQUESTED",
            "SCHEDULED, REQUESTED",
            "SCHEDULED, DELIVERED",
            "SCHEDULED, SCHEDULED",
            "IN_PRODUCTION, SCHEDULED",
            "IN_PRODUCTION, IN_PRODUCTION",
            "DELIVERED, IN_PRODUCTION",
            "DELIVERED, DELIVERED",
            "COMPLETED, DELIVERED",
            "CANCELLED, SCHEDULED"
    })
    void refusesEveryOtherMoveAndLeavesTheRequestAlone(String stored, MediaBoardStatus target) {
        MediaRequest request = requestIn(stored);

        assertThrows(MediaConflictException.class, () -> request.moveTo(target, "coord@test.local", T1));

        assertEquals(stored, request.getStatus());
        assertEquals(T0, request.getUpdatedAt());
        assertNull(request.getDeliveredAt());
    }

    @Test
    void theBoardShowsAClosedRequestAsDeliveredAndHidesACancelledOne() {
        assertEquals(MediaBoardStatus.DELIVERED, MediaBoardStatus.shown("COMPLETED").orElseThrow());
        assertEquals(MediaBoardStatus.DELIVERED, MediaBoardStatus.shown("DELIVERED").orElseThrow());
        assertEquals(MediaBoardStatus.IN_PRODUCTION, MediaBoardStatus.shown("IN_PRODUCTION").orElseThrow());
        assertEquals(true, MediaBoardStatus.shown("CANCELLED").isEmpty());
    }

    @Test
    void assignsTheBoardFieldsWhileTheRequestIsOpen() {
        MediaRequest request = requestIn("SCHEDULED");
        UUID studio = UUID.randomUUID();

        request.assign(studio, "Katia", T1, "Highlights reel", T1);

        assertEquals(studio, request.getStudioId());
        assertEquals("Katia", request.getAssignee());
        assertEquals(T1, request.getScheduledAt());
        assertEquals("Highlights reel", request.getDescription());
        assertNotNull(request.getUpdatedAt());
    }

    @ParameterizedTest
    @CsvSource({"DELIVERED", "COMPLETED", "CANCELLED"})
    void refusesToChangeTheDetailsOfAFinishedRequest(String stored) {
        MediaRequest request = requestIn(stored);

        assertThrows(MediaConflictException.class,
                () -> request.assign(null, "Katia", null, null, T1));
        assertNull(request.getAssignee());
    }

    private static MediaRequest requestIn(String stored) {
        MediaRequest request = new MediaRequest(
                UUID.randomUUID(), "MRQ-TEST", null, null, "owner@test.local", "Podcast", T0);
        if (!"IN_PRODUCTION".equals(stored)) {
            // The constructor starts every request in production; tests need the other states too.
            org.springframework.test.util.ReflectionTestUtils.setField(request, "status", stored);
        }
        return request;
    }
}
