package org.unipod.unipoderpbackend.service.auth;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.CapabilityResponse;
import org.unipod.unipoderpbackend.dto.auth.MeResponse;
import org.unipod.unipoderpbackend.dto.auth.UserProfileResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationPolicy;

@Service
public class MeService {
    static final String SUBMITTER_HOME = "MY_SUBMISSIONS";
    static final String STAFF_HOME = "STAFF_HOME";

    private final ApiUserRepository users;
    private final UserProfileRepository profiles;
    private final AuthorizationPolicy policy;

    public MeService(ApiUserRepository users, UserProfileRepository profiles, AuthorizationPolicy policy) {
        this.users = users;
        this.profiles = profiles;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public MeResponse me(Actor actor) {
        ApiUser user = users.findByEmailIgnoreCase(actor.email())
                .filter(ApiUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid account is required."));
        UserProfileResponse profile = profiles.findById(user.getId())
                .map(UserProfileResponse::from)
                .orElse(null);
        List<CapabilityResponse> capabilities = policy.capabilitiesFor(user.getRole()).entrySet().stream()
                .map(entry -> new CapabilityResponse(entry.getKey(), entry.getValue()))
                .toList();
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isMustChangePassword(),
                user.getRole() == ApiUserRole.SUBMITTER ? SUBMITTER_HOME : STAFF_HOME,
                actor.departmentIds(),
                profile,
                capabilities
        );
    }
}
