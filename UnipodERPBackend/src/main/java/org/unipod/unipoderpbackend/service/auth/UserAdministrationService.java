package org.unipod.unipoderpbackend.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.auth.AdminCreateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.AdminUpdateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.AdminUserResponse;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.auth.RoleResponse;
import org.unipod.unipoderpbackend.dto.auth.UpdateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.UserProfileResponse;
import org.unipod.unipoderpbackend.dto.auth.UserSummaryResponse;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.auth.UserProfile;
import org.unipod.unipoderpbackend.model.sector.SectorMember;
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

    @Transactional
    public AdminUserResponse createManagedUser(AdminCreateUserRequest request, Actor actor) {
        if (request.role() == ApiUserRole.ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The administrator role is protected.");
        }
        ApiUserResponse created = createUser(new CreateApiUserRequest(
                request.email(),
                request.password(),
                request.role(),
                request.fullName()
        ), actor);
        ApiUser user = require(created.id());
        if (request.departmentId() != null) {
            sectors.requireActive(request.departmentId());
            sectorMembers.insertIfAbsent(UUID.randomUUID(), request.departmentId(), user.getEmail());
            audit.log(AuditEvent.changed(
                    actor,
                    "user.departments_changed",
                    "user",
                    user.getId(),
                    Map.of("departmentIds", List.of()),
                    Map.of("departmentIds", List.of(request.departmentId().toString()))
            ));
        }
        return adminSummary(user);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> adminList(Actor actor) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        List<AdminUserResponse> result = new ArrayList<>();
        int pageNumber = 0;
        Page<ApiUser> page;
        do {
            page = users.search(null, null, "", PageRequest.of(pageNumber++, 500));
            result.addAll(adminSummaries(page.getContent()));
        } while (!page.isLast());
        return List.copyOf(result);
    }

    private List<AdminUserResponse> adminSummaries(List<ApiUser> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserProfileResponse> profileByUserId = profiles.findAllById(
                        found.stream().map(ApiUser::getId).toList())
                .stream()
                .collect(Collectors.toMap(UserProfile::getUserId, UserProfileResponse::from));
        Map<String, Set<UUID>> departmentsByEmail = sectorMembers.findAllBySubjectIn(
                        found.stream().map(ApiUser::getEmail).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        SectorMember::getSubject,
                        Collectors.mapping(SectorMember::getSectorId, Collectors.toSet())
                ));
        return found.stream()
                .map(user -> adminSummary(
                        user,
                        profileByUserId.get(user.getId()),
                        departmentsByEmail.getOrDefault(user.getEmail(), Set.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getAdminUser(Actor actor, UUID id) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        return adminSummary(require(id));
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
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A user version is required.");
        }
        applyUpdate(actor, user, request.role(), request.departmentIds(), request.version(),
                request.departmentIds() != null);
        return summary(user);
    }

    @Transactional
    public AdminUserResponse updateManagedUser(Actor actor, UUID id, AdminUpdateUserRequest request) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A user version is required.");
        }
        Set<UUID> departmentIds = null;
        if (request.departmentIdProvided()) {
            if (request.departmentId() == null) {
                departmentIds = Set.of();
            } else {
                Set<UUID> current = sectorMembers.findSectorIdsBySubject(user.getEmail());
                if (current.size() > 1 && !current.contains(request.departmentId())) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This account belongs to multiple departments. Update its memberships through the user administration API."
                    );
                }
                departmentIds = current.size() > 1 ? current : Set.of(request.departmentId());
            }
        }
        applyUpdate(actor, user, request.role(), departmentIds, request.version(),
                request.departmentIdProvided());
        return adminSummary(user);
    }

    private void applyUpdate(
            Actor actor,
            ApiUser user,
            ApiUserRole requestedRole,
            Set<UUID> requestedDepartmentIds,
            Long expectedVersion,
            boolean departmentIdsProvided
    ) {
        verifyVersion(user, expectedVersion);
        Instant now = clock.instant();
        boolean changed = false;
        if (requestedRole != null && requestedRole != user.getRole()) {
            if (requestedRole == ApiUserRole.ADMINISTRATOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The administrator role is protected.");
            }
            refuseSelf(actor, user, "change your own role");
            refuseLastAdministrator(user);
            ApiUserRole before = user.getRole();
            user.changeRole(requestedRole, now);
            changed = true;
            audit.log(AuditEvent.changed(actor, "user.role_changed", "user", user.getId(),
                    Map.of("role", before.name()), Map.of("role", requestedRole.name())));
        }
        if (departmentIdsProvided) {
            Set<UUID> before = sectorMembers.findSectorIdsBySubject(user.getEmail());
            Set<UUID> after = Set.copyOf(requestedDepartmentIds);
            difference(after, before).forEach(sectors::requireActive);
            for (UUID removed : difference(before, after)) {
                sectorMembers.deleteBySectorIdAndSubject(removed, user.getEmail());
            }
            for (UUID added : difference(after, before)) {
                sectorMembers.insertIfAbsent(UUID.randomUUID(), added, user.getEmail());
            }
            if (!before.equals(after)) {
                changed = true;
                audit.log(AuditEvent.changed(actor, "user.departments_changed", "user", user.getId(),
                        Map.of("departmentIds", sorted(before)), Map.of("departmentIds", sorted(after))));
            }
        }
        if (changed) {
            user.touch(now);
            flushVersionedUser(user);
        }
    }

    @Transactional
    public UserSummaryResponse deactivate(Actor actor, UUID id) {
        return deactivate(actor, id, null);
    }

    @Transactional
    public UserSummaryResponse deactivate(Actor actor, UUID id, Long expectedVersion) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        verifyVersion(user, expectedVersion);
        if (!user.isEnabled()) {
            return summary(user);
        }
        refuseSelf(actor, user, "deactivate your own account");
        refuseLastAdministrator(user);
        user.deactivate(clock.instant());
        flushVersionedUser(user);
        sessions.endAllSessions(user.getId());
        audit.log(AuditEvent.changed(actor, "user.deactivated", "user", user.getId(),
                Map.of("active", true), Map.of("active", false)));
        return summary(user);
    }

    @Transactional
    public UserSummaryResponse reactivate(Actor actor, UUID id) {
        return reactivate(actor, id, null);
    }

    @Transactional
    public UserSummaryResponse reactivate(Actor actor, UUID id, Long expectedVersion) {
        authorization.authorize(actor, AuthorizationAction.USER_MANAGE, USERS);
        ApiUser user = require(id);
        verifyVersion(user, expectedVersion);
        if (user.isEnabled()) {
            return summary(user);
        }
        user.reactivate(clock.instant());
        flushVersionedUser(user);
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

    private AdminUserResponse adminSummary(ApiUser user) {
        UserProfileResponse profile = profiles.findById(user.getId()).map(UserProfileResponse::from).orElse(null);
        Set<UUID> departmentIds = sectorMembers.findSectorIdsBySubject(user.getEmail());
        return adminSummary(user, profile, departmentIds);
    }

    private AdminUserResponse adminSummary(
            ApiUser user,
            UserProfileResponse profile,
            Set<UUID> departmentIds
    ) {
        String fullName = profile != null && profile.fullName() != null && !profile.fullName().isBlank()
                ? profile.fullName()
                : user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName()
                        : user.getEmail().substring(0, user.getEmail().indexOf('@'));
        List<UUID> sortedDepartmentIds = departmentIds.stream().sorted().toList();
        return AdminUserResponse.from(user, fullName, sortedDepartmentIds);
    }

    private static void verifyVersion(ApiUser user, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != user.getVersion()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This account changed elsewhere. Refresh and try again."
            );
        }
    }

    private void flushVersionedUser(ApiUser user) {
        try {
            users.saveAndFlush(user);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This account changed elsewhere. Refresh and try again.",
                    exception
            );
        }
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
