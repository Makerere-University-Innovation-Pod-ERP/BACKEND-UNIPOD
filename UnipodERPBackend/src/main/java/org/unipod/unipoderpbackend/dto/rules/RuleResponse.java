package org.unipod.unipoderpbackend.dto.rules;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.rules.DepartmentRule;
import org.unipod.unipoderpbackend.model.rules.RuleScope;

public record RuleResponse(
        UUID id,
        String title,
        String body,
        RuleScope scope,
        UUID scopeId,
        boolean mustAccept,
        int version,
        boolean published,
        Instant updatedAt) {

    public static RuleResponse from(DepartmentRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getTitle(),
                rule.getBody(),
                rule.getScope(),
                rule.getScopeId(),
                rule.isMustAccept(),
                rule.getRuleVersion(),
                rule.isPublished(),
                rule.getUpdatedAt());
    }
}
