package org.unipod.unipoderpbackend.service.operations;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

/**
 * The sector names that count as each operations department (D-046). The sector list is Alvin's and has no
 * department code, so the match is by name: a coordinator of the sector "Telecoms" manages the Telecoms department.
 */
public final class DepartmentSectorNames {
    private DepartmentSectorNames() {
    }

    /** Lower-case names, the same aliases {@link DepartmentCode#fromPath} accepts. */
    public static Set<String> of(DepartmentCode department) {
        return switch (department) {
            case MULTIMEDIA -> Set.of("multimedia", "media");
            case TEXTILE -> Set.of("textile", "textiles");
            case CNC -> Set.of("cnc");
            case TELECOMS -> Set.of("telecoms", "telecom", "telecommunications", "telecom lab");
        };
    }

    /** Lower case, with spaces, dashes and underscores all counting as one space ("Telecom-Lab" is "telecom lab"). */
    public static String normalise(String sectorName) {
        return sectorName.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
    }

    public static Optional<DepartmentCode> codeOf(String sectorName) {
        if (sectorName == null) {
            return Optional.empty();
        }
        String normalised = normalise(sectorName);
        for (DepartmentCode department : DepartmentCode.values()) {
            if (of(department).contains(normalised)) {
                return Optional.of(department);
            }
        }
        return Optional.empty();
    }
}
