package org.unipod.unipoderpbackend.model.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
