package org.unipod.unipoderpbackend.service.partner;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MOU settings (D-006, D-020). The day counts say how long before the renewal date a partner moves
 * into each warning status. Renewal alerts go to active users holding one of {@code alertRoles}
 * plus any {@code alertRecipients}; roles are kept as names so this feature never imports the
 * auth feature's enum.
 */
@ConfigurationProperties("operations.partners")
public record PartnerMouProperties(
        @DefaultValue("60") int renewalDueDays,
        @DefaultValue("30") int expiringSoonDays,
        @DefaultValue({"ADMINISTRATOR", "DEPARTMENT_COORDINATOR"}) Set<String> alertRoles,
        @DefaultValue Set<String> alertRecipients) {

    public PartnerMouProperties {
        if (expiringSoonDays < 0 || renewalDueDays < expiringSoonDays) {
            throw new IllegalArgumentException(
                    "operations.partners needs 0 <= expiring-soon-days <= renewal-due-days.");
        }
        alertRoles = clean(alertRoles, true);
        alertRecipients = clean(alertRecipients, false);
    }

    private static Set<String> clean(Set<String> values, boolean upperCase) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> upperCase ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
