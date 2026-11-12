package org.unipod.unipoderpbackend.model.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The record being acted on, reduced to the facts the policy needs.
 *
 * @param type           what kind of record, e.g. "submission"
 * @param id             the record id (null when creating a new record)
 * @param ownerEmail     who owns it (null if nobody)
 * @param assigneeEmails who it is assigned to (empty if nobody)
 * @param departmentId   which department it belongs to (null if none)
 */
public record AuthorizationResource(
        String type,
        UUID id,
        String ownerEmail,
        Set<String> assigneeEmails,
        UUID departmentId
) {
    public AuthorizationResource {
        Objects.requireNonNull(type, "type");
        ownerEmail = normalise(ownerEmail);
        assigneeEmails = assigneeEmails == null
                ? Set.of()
                : assigneeEmails.stream()
                        .filter(Objects::nonNull)
                        .map(AuthorizationResource::normalise)
                        .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
