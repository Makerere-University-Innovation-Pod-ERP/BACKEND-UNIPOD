package org.unipod.unipoderpbackend.dto.training;

import java.util.List;

public record TrainingOverviewResponse(
        long upcomingSessions,
        long seatsFilled,
        long totalSeats,
        long mentorshipFollowUps,
        List<SessionResponse> sessions,
        List<FollowUpResponse> followUps) {
}
