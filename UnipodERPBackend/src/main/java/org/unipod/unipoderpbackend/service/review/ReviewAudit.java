package org.unipod.unipoderpbackend.service.review;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

/**
 * Audit entries for review writes, recorded through the shared {@link AuditTrail} inside the caller's
 * transaction (AuditService.log is MANDATORY propagation).
 *
 * <p>Entries use entity type {@value #ENTITY_TYPE} with the brief id as entity id. The brief history
 * ({@code GET /cases/{id}/activity}), which submitters can read, lists only entity type "submission",
 * so these entries never reach a submitter; staff with audit access see them in
 * {@code GET /activity?entityType=review}.
 *
 * <p>Only structural values are recorded. Never add free text (comments, notes), reviewer emails,
 * departments or decisions here.
 */
@Component
public class ReviewAudit {
    public static final String ENTITY_TYPE = "review";
    public static final String SETTINGS_SAVED = "submission.review_settings_saved";
    public static final String REVIEWER_ASSIGNED = "submission.reviewer_assigned";
    public static final String REVIEWER_REMOVED = "submission.reviewer_removed";
    public static final String REVIEW_SUBMITTED = "submission.review_submitted";
    public static final String REVIEW_FOLLOW_UP_ADDED = "submission.review_follow_up_added";

    private final AuditTrail trail;

    public ReviewAudit(AuditTrail trail) {
        this.trail = trail;
    }

    /** Settings values as recorded; keys are always present (dueDate may be null). */
    static Map<String, Object> settingsDetails(ReviewSettings settings) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("round", settings.getRound());
        details.put("mode", settings.getMode().name());
        details.put("reviewsNeeded", settings.getReviewsNeeded());
        details.put("dueDate", settings.getDueDate() == null ? null : settings.getDueDate().toString());
        return details;
    }

    /** {@code before} is null when the settings were just created. */
    void settingsSaved(String actor, UUID briefId, Map<String, Object> before, ReviewSettings saved) {
        trail.record(actor, SETTINGS_SAVED, ENTITY_TYPE, briefId, before, settingsDetails(saved));
    }

    void reviewerAssigned(String actor, ReviewAssignment assignment) {
        trail.record(actor, REVIEWER_ASSIGNED, ENTITY_TYPE, assignment.getBriefId(), null, positionDetails(assignment));
    }

    void reviewerRemoved(String actor, ReviewAssignment assignment) {
        trail.record(actor, REVIEWER_REMOVED, ENTITY_TYPE, assignment.getBriefId(), null, positionDetails(assignment));
    }

    void reviewSubmitted(String actor, ReviewAssignment assignment) {
        trail.record(actor, REVIEW_SUBMITTED, ENTITY_TYPE, assignment.getBriefId(), null, positionDetails(assignment));
    }

    void followUpAdded(String actor, ReviewAssignment assignment) {
        trail.record(actor, REVIEW_FOLLOW_UP_ADDED, ENTITY_TYPE, assignment.getBriefId(), null,
                positionDetails(assignment));
    }

    private static Map<String, Object> positionDetails(ReviewAssignment assignment) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("round", assignment.getRound());
        details.put("position", assignment.getPosition());
        return details;
    }
}
