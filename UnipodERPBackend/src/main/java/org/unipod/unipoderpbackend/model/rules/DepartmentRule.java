package org.unipod.unipoderpbackend.model.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "department_rules")
public class DepartmentRule {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RuleScope scope;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "must_accept", nullable = false)
    private boolean mustAccept;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion = 1;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DepartmentRule() {
    }

    public DepartmentRule(
            UUID id,
            String title,
            String body,
            RuleScope scope,
            UUID scopeId,
            boolean mustAccept,
            Instant now) {
        this.id = Objects.requireNonNull(id);
        this.title = Objects.requireNonNull(title);
        this.body = Objects.requireNonNull(body);
        this.scope = Objects.requireNonNull(scope);
        this.scopeId = scopeId;
        this.mustAccept = mustAccept;
        this.published = false;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public void update(
            String title,
            String body,
            RuleScope scope,
            UUID scopeId,
            Boolean mustAccept,
            Boolean published,
            Instant now) {
        boolean contentChanged = !this.title.equals(title)
                || !this.body.equals(body)
                || this.scope != scope
                || !Objects.equals(this.scopeId, scopeId)
                || (mustAccept != null && this.mustAccept != mustAccept);
        boolean publishChanged = published != null && this.published != published;
        if (!contentChanged && !publishChanged) {
            return;
        }
        this.title = title;
        this.body = body;
        this.scope = scope;
        this.scopeId = scopeId;
        if (mustAccept != null) {
            this.mustAccept = mustAccept;
        }
        if (published != null) {
            this.published = published;
        }
        if (contentChanged) {
            this.ruleVersion++;
        }
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        if (published) {
            published = false;
            updatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public RuleScope getScope() {
        return scope;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public boolean isMustAccept() {
        return mustAccept;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
