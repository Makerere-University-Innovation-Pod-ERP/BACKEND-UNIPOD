package org.unipod.unipoderpbackend.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.auth.RoleResponse;
import org.unipod.unipoderpbackend.dto.auth.UpdateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.UserProfileResponse;
import org.unipod.unipoderpbackend.dto.auth.UserSummaryResponse;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@Service
public class UserAdministrationService {
    private static final AuthorizationResource USERS =
            new AuthorizationResource("user", null, null, Set.of(), null);

    private final AuthenticationService authentication;
    private final AuditService audit;
    private final ApiUserRepository users;
    private final UserProfileRepository profiles;
    private final SectorMemberRepository sectorMembers;
    private final SectorService sectors;
    private final AuthorizationService authorization;
    private final SessionService sessions;
    private final Clock clock;

    public UserAdministrationService(
            AuthenticationService authentication,
            AuditService audit,
            ApiUserRepository users,
            UserProfileRepository profiles,
            SectorMemberRepository sectorMembers,
            SectorService sectors,
            AuthorizationService authorization,
            SessionService sessions,
            Clock clock
    ) {
        this.authentication = authentication;
        this.audit = audit;
        this.users = users;
        this.profiles = profiles;
        this.sectorMembers = sectorMembers;
        this.sectors = sectors;
        this.authorization = authorization;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public ApiUserResponse createUser(CreateApiUserRequest request, Actor actor) {
        ApiUserResponse created = authentication.createUser(request);
        audit.log(AuditEvent.created(actor, "user.created", "user", created.id(), created));
        return created;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> list(
            Actor actor, ApiUserRole role, Boolean active, String query, int page, int size) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Page<ApiUser> found = users.search(role, active, search, PageRequest.of(page, size));
        return PageResponse.from(found.map(this::summary));
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse get(Actor actor, UUID id) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        return summary(require(id));
    }

    @Transactional
    public UserSummaryResponse update(Actor actor, UUID id, UpdateUserRequest request) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        Instant now = clock.instant();
        if (request.role() != null && request.role() != user.getRole()) {
            refuseSelf(actor, user, "change your own role");
            refuseLastAdministrator(user);
            ApiUserRole before = user.getRole();
            user.changeRole(request.role(), now);
            users.saveAndFlush(user);
            audit.log(AuditEvent.changed(actor, "user.role_changed", "user", user.getId(),
                    Map.of("role", before.name()), Map.of("role", request.role().name())));
        }
        if (request.departmentIds() != null) {
            Set<UUID> before = sectorMembers.findSectorIdsBySubject(user.getEmail());
            Set<UUID> after = Set.copyOf(request.departmentIds());
            after.forEach(sectors::requireActive);
            for (UUID removed : difference(before, after)) {
                sectorMembers.deleteBySectorIdAndSubject(removed, user.getEmail());
            }
            for (UUID added : difference(after, before)) {
                sectorMembers.insertIfAbsent(UUID.randomUUID(), added, user.getEmail());
            }
            if (!before.equals(after)) {
                audit.log(AuditEvent.changed(actor, "user.departments_changed", "user", user.getId(),
                        Map.of("departmentIds", sorted(before)), Map.of("departmentIds", sorted(after))));
            }
        }
        return summary(user);
    }

    @Transactional
    public UserSummaryResponse deactivate(Actor actor, UUID id) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        if (!user.isEnabled()) {
            return summary(user);
        }
        refuseSelf(actor, user, "deactivate your own account");
        refuseLastAdministrator(user);
        user.deactivate(clock.instant());
        users.saveAndFlush(user);
        sessions.endAllSessions(user.getId());
        audit.log(AuditEvent.changed(actor, "user.deactivated", "user", user.getId(),
                Map.of("active", true), Map.of("active", false)));
        return summary(user);
    }

    @Transactional
    public UserSummaryResponse reactivate(Actor actor, UUID id) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        if (user.isEnabled()) {
            return summary(user);
        }
        user.reactivate(clock.instant());
        users.saveAndFlush(user);
        audit.log(AuditEvent.changed(actor, "user.reactivated", "user", user.getId(),
                Map.of("active", false), Map.of("active", true)));
        return summary(user);
    }

    public List<RoleResponse> roles() {
        return Arrays.stream(ApiUserRole.values()).map(RoleResponse::from).toList();
    }

    private ApiUser require(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The user was not found."));
    }

    private UserSummaryResponse summary(ApiUser user) {
        UserProfileResponse profile = profiles.findById(user.getId()).map(UserProfileResponse::from).orElse(null);
        return UserSummaryResponse.from(user, sectorMembers.findSectorIdsBySubject(user.getEmail()), profile);
    }

    private static void refuseSelf(Actor actor, ApiUser user, String what) {
        if (actor.email().equals(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot " + what + ".");
        }
    }

    private void refuseLastAdministrator(ApiUser user) {
        if (user.getRole() == ApiUserRole.ADMINISTRATOR
                && user.isEnabled()
                && users.countByRoleAndEnabledTrue(ApiUserRole.ADMINISTRATOR) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active administrator cannot be removed.");
        }
    }

    private static Set<UUID> difference(Set<UUID> left, Set<UUID> right) {
        Set<UUID> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static List<String> sorted(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }
}
