package org.unipod.unipoderpbackend.service.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;

@Component
public class AuditTrail {
    public static final String SUBMISSION = "submission";

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuditService audit;
    private final ApiUserRepository users;

    public AuditTrail(AuditService audit, ApiUserRepository users) {
        this.audit = audit;
        this.users = users;
    }

    public void record(String actorEmail, String action, UUID submissionId, Object before, Object after) {
        record(actorEmail, action, SUBMISSION, submissionId, before, after);
    }

    public void record(
            String actorEmail,
            String action,
            String entityType,
            UUID entityId,
            Object before,
            Object after
    ) {
        audit.log(before == null
                ? AuditEvent.created(actor(actorEmail), action, entityType, entityId, after)
                : AuditEvent.changed(actor(actorEmail), action, entityType, entityId, before, after));
    }

    public static Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            Object name = keysAndValues[index];
            Object value = keysAndValues[index + 1];
            if (name != null && value != null) {
                details.put(String.valueOf(name), value);
            }
        }
        return details;
    }

    private Actor actor(String actorEmail) {
        String email = actorEmail == null || actorEmail.isBlank()
                ? "system"
                : actorEmail.trim().toLowerCase(Locale.ROOT);
        ApiUserRole role = users.findByEmailIgnoreCase(email)
                .map(ApiUser::getRole)
                .orElseGet(AuditTrail::roleFromSecurityContext);
        return new Actor(email, role, Set.of());
    }

    private static ApiUserRole roleFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String value = authority.getAuthority();
                if (value != null && value.startsWith(ROLE_PREFIX)) {
                    try {
                        return ApiUserRole.valueOf(value.substring(ROLE_PREFIX.length()));
                    } catch (IllegalArgumentException ignored) {
                        return ApiUserRole.SUBMITTER;
                    }
                }
            }
        }
        return ApiUserRole.SUBMITTER;
    }
}
