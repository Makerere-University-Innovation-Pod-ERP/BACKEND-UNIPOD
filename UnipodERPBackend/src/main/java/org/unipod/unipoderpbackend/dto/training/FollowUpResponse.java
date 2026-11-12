package org.unipod.unipoderpbackend.dto.training;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.training.FollowUpPriority;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;

public record FollowUpResponse(
        UUID id,
        String title,
        String mentee,
        String mentor,
        LocalDate scheduledDate,
        String focus,
        FollowUpStatus status,
        String notes,
        FollowUpPriority priority,
        long version) {

    public static FollowUpResponse from(MentorshipFollowUp followUp) {
        return new FollowUpResponse(
                followUp.getId(),
                followUp.getTitle(),
                followUp.getMentee(),
                followUp.getMentor(),
                followUp.getScheduledDate(),
                followUp.getFocus(),
                followUp.getStatus(),
                followUp.getNotes(),
                followUp.getPriority(),
                followUp.getVersion());
    }
}
