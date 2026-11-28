package org.unipod.unipoderpbackend.dto.rules;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.rules.RuleScope;

public record CreateRuleRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String body,
        @NotNull RuleScope scope,
        UUID scopeId,
        @NotNull Boolean mustAccept,
        @AssertFalse Boolean published) {
}
