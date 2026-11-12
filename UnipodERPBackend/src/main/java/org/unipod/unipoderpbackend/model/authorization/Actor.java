package org.unipod.unipoderpbackend.model.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

/**
 * The signed-in person asking to do something.
 *
 * @param email         the JWT subject, normalised to lower case
 * @param role          the user's single role
 * @param departmentIds departments the user belongs to (empty until the team decides where departments live)
 */
public record Actor(String email, ApiUserRole role, Set<UUID> departmentIds) {
    public Actor {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
        email = email.trim().toLowerCase(Locale.ROOT);
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
    }
}
