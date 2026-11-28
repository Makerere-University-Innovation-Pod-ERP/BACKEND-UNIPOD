package org.unipod.unipoderpbackend.dto.rules;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.rules.RuleScope;

public record UpdateRuleRequest(
        @Size(max = 120) String title,
        @Size(max = 4000) String body,
        RuleScope scope,
        UUID scopeId,
        Boolean mustAccept,
        Boolean published) {
}
