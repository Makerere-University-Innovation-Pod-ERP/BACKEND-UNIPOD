package org.unipod.unipoderpbackend.service.equipment;

import java.util.Locale;
import java.util.Set;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;

/**
 * The details of a photo sent with a machine report. Only the file name is kept: photos are not stored (D-042),
 * so the name is just a reference for whoever reads the report.
 */
public record PhotoMeta(String fileName, String contentType) {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final int NAME_MAX = 255;

    String checkedName() {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new InvalidEquipmentException("The photo must be a JPG or PNG image.");
        }
        String name = fileName == null ? "" : fileName.trim();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "photo";
        }
        return name.length() <= NAME_MAX ? name : name.substring(0, NAME_MAX);
    }
}
