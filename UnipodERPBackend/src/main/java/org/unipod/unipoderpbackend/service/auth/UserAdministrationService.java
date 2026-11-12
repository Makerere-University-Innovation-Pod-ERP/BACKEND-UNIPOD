package org.unipod.unipoderpbackend.service.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.service.audit.AuditService;

/**
 * Admin user management (AD1). Wraps the existing user creation in one transaction
 * together with its audit line. List, patch, deactivate and reactivate will live here too.
 */
@Service
public class UserAdministrationService {
    private final AuthenticationService authentication;
    private final AuditService audit;

    public UserAdministrationService(AuthenticationService authentication, AuditService audit) {
        this.authentication = authentication;
        this.audit = audit;
    }

    @Transactional
    public ApiUserResponse createUser(CreateApiUserRequest request, Actor actor) {
        ApiUserResponse created = authentication.createUser(request);
        // ApiUserResponse has no password hash, so nothing secret goes into the log.
        audit.log(AuditEvent.created(actor, "user.created", "user", created.id(), created));
        return created;
    }
}
