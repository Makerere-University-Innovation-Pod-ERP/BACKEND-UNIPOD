package org.unipod.unipoderpbackend.repository.training;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;

public interface MentorshipFollowUpRepository extends JpaRepository<MentorshipFollowUp, UUID> {
    // Matches ix_mentorship_follow_ups_scheduled_date: soonest first.
    @Query("select f from MentorshipFollowUp f order by f.scheduledDate asc, f.id asc")
    List<MentorshipFollowUp> findAllInScheduleOrder();
}
