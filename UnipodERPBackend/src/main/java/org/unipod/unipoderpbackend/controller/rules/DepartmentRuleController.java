package org.unipod.unipoderpbackend.controller.rules;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.rules.CreateRuleRequest;
import org.unipod.unipoderpbackend.dto.rules.RuleResponse;
import org.unipod.unipoderpbackend.dto.rules.UpdateRuleRequest;
import org.unipod.unipoderpbackend.service.rules.DepartmentRuleService;

@RestController
@RequestMapping("/rules")
public class DepartmentRuleController {
    private final DepartmentRuleService rules;

    public DepartmentRuleController(DepartmentRuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RuleResponse> list(Authentication authentication) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR"));
        return rules.list(administrator);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public RuleResponse create(@Valid @RequestBody CreateRuleRequest request, Authentication authentication) {
        return rules.create(authentication.getName(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public RuleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRuleRequest request,
            Authentication authentication) {
        return rules.update(authentication.getName(), id, request);
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public RuleResponse retire(@PathVariable UUID id, Authentication authentication) {
        return rules.retire(authentication.getName(), id);
    }
}
