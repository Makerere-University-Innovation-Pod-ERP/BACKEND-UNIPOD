package org.unipod.unipoderpbackend.service.rules;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.FieldErrorResponse;
import org.unipod.unipoderpbackend.dto.rules.CreateRuleRequest;
import org.unipod.unipoderpbackend.dto.rules.RuleResponse;
import org.unipod.unipoderpbackend.dto.rules.UpdateRuleRequest;
import org.unipod.unipoderpbackend.exception.rules.InvalidRuleException;
import org.unipod.unipoderpbackend.model.rules.DepartmentRule;
import org.unipod.unipoderpbackend.model.rules.RuleScope;
import org.unipod.unipoderpbackend.repository.rules.DepartmentRuleRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

@Service
public class DepartmentRuleService {
    private static final String ENTITY_TYPE = "rule";

    private final DepartmentRuleRepository rules;
    private final SectorRepository sectors;
    private final AuditTrail audit;
    private final Clock clock;

    public DepartmentRuleService(
            DepartmentRuleRepository rules,
            SectorRepository sectors,
            AuditTrail audit,
            Clock clock) {
        this.rules = rules;
        this.sectors = sectors;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(boolean includeDrafts) {
        List<DepartmentRule> items = includeDrafts
                ? rules.findAllByOrderByUpdatedAtDescTitleAsc()
                : rules.findAllByPublishedTrueOrderByUpdatedAtDescTitleAsc();
        return items.stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse create(String actorEmail, CreateRuleRequest request) {
        validateScope(request.scope(), request.scopeId());
        DepartmentRule rule = new DepartmentRule(
                UUID.randomUUID(),
                requiredText(request.title(), "title"),
                requiredText(request.body(), "body"),
                request.scope(),
                request.scopeId(),
                request.mustAccept(),
                clock.instant());
        RuleResponse response = RuleResponse.from(rules.saveAndFlush(rule));
        audit.record(actorEmail, "rule.created", ENTITY_TYPE, rule.getId(), null, snapshot(response));
        return response;
    }

    @Transactional
    public RuleResponse update(String actorEmail, UUID id, UpdateRuleRequest request) {
        DepartmentRule rule = findRule(id);
        RuleResponse before = RuleResponse.from(rule);
        String title = request.title() == null ? rule.getTitle() : requiredText(request.title(), "title");
        String body = request.body() == null ? rule.getBody() : requiredText(request.body(), "body");
        RuleScope scope = request.scope() == null ? rule.getScope() : request.scope();
        if (scope == RuleScope.GENERAL && request.scopeId() != null) {
            throw invalid("scopeId", "A general rule cannot be assigned to a department.");
        }
        UUID scopeId = scope == RuleScope.GENERAL
                ? null
                : request.scopeId() == null ? rule.getScopeId() : request.scopeId();

        validateScope(scope, scopeId);

        rule.update(title, body, scope, scopeId, request.mustAccept(), request.published(), clock.instant());
        RuleResponse response = RuleResponse.from(rules.saveAndFlush(rule));
        String action = !before.published() && response.published()
                ? "rule.published"
                : before.published() && !response.published()
                    ? "rule.unpublished"
                    : "rule.changed";
        audit.record(actorEmail, action, ENTITY_TYPE, rule.getId(), snapshot(before), snapshot(response));
        return response;
    }

    @Transactional
    public RuleResponse retire(String actorEmail, UUID id) {
        DepartmentRule rule = findRule(id);
        RuleResponse before = RuleResponse.from(rule);
        rule.retire(clock.instant());
        RuleResponse response = RuleResponse.from(rules.saveAndFlush(rule));
        audit.record(actorEmail, "rule.retired", ENTITY_TYPE, rule.getId(), snapshot(before), snapshot(response));
        return response;
    }

    private DepartmentRule findRule(UUID id) {
        return rules.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found."));
    }

    private void validateScope(RuleScope scope, UUID scopeId) {
        if (scope == RuleScope.GENERAL) {
            if (scopeId != null) {
                throw invalid("scopeId", "A general rule cannot be assigned to a department.");
            }
            return;
        }
        if (scopeId == null) {
            throw invalid("scopeId", "Choose a department for a department-specific rule.");
        }
        boolean active = sectors.findById(scopeId).map(sector -> sector.isActive()).orElse(false);
        if (!active) {
            throw invalid("scopeId", "The chosen department is not available.");
        }
    }

    private static String requiredText(String value, String field) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty() || cleaned.indexOf('\0') >= 0) {
            throw invalid(field, "Rule " + field + " is required.");
        }
        return cleaned;
    }

    private static InvalidRuleException invalid(String field, String message) {
        return new InvalidRuleException(new FieldErrorResponse(field, message));
    }

    private static Map<String, Object> snapshot(RuleResponse rule) {
        return AuditTrail.details(
                "scope", rule.scope(),
                "scopeId", rule.scopeId(),
                "mustAccept", rule.mustAccept(),
                "version", rule.version(),
                "published", rule.published());
    }
}
