package org.unipod.unipoderpbackend.security;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;

@Component
public class CurrentActor {
    private static final String ROLE_PREFIX = "ROLE_";

    private final SectorMemberRepository sectorMembers;

    public CurrentActor(SectorMemberRepository sectorMembers) {
        this.sectorMembers = sectorMembers;
    }

    public Actor from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Not signed in.");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        if (roles.size() != 1) {
            throw new AccessDeniedException("Expected exactly one role.");
        }
        ApiUserRole role;
        try {
            role = ApiUserRole.valueOf(roles.get(0));
        } catch (IllegalArgumentException unknownRole) {
            throw new AccessDeniedException("Unknown role.");
        }
        return new Actor(
                authentication.getName(),
                role,
                sectorMembers.findSectorIdsBySubject(authentication.getName())
        );
    }
}
