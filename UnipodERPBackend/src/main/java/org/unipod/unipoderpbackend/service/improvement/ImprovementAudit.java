package org.unipod.unipoderpbackend.service.improvement;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

/**
 * Audit entry for accepting an improved version, recorded through the shared {@link AuditTrail} inside the caller's
 * transaction (AuditService.log is MANDATORY propagation).
 *
 * <p>Entries use entity type {@value #ENTITY_TYPE} with the brief id as entity id. The brief history
 * ({@code GET /cases/{id}/activity}) lists only entity type "submission", so these entries are staff-only:
 * they appear in {@code GET /activity?entityType=improvement}.
 *
 * <p>Only structural values are recorded. Never add free text here: no guidance note and no requested-change text.
 */
@Component
public class ImprovementAudit {
    public static final String ENTITY_TYPE = "improvement";
    public static final String IMPROVED_VERSION_ACCEPTED = "submission.improved_version_accepted";

    private final AuditTrail trail;

    public ImprovementAudit(AuditTrail trail) {
        this.trail = trail;
    }

    void improvedVersionAccepted(String actor, RefinementRequest request, int versionNumber) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cycle", request.getCycle());
        details.put("versionNumber", versionNumber);
        trail.record(actor, IMPROVED_VERSION_ACCEPTED, ENTITY_TYPE, request.getBriefId(), null, details);
    }
}
