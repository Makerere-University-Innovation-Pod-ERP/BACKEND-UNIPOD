package org.unipod.unipoderpbackend.model.operations;

public enum DepartmentCode {
    MULTIMEDIA,
    TEXTILE,
    CNC,
    TELECOMS;

    public static DepartmentCode fromPath(String raw) {
        if (raw == null) throw new IllegalArgumentException("Department is required");
        String n = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "MULTIMEDIA", "MEDIA" -> MULTIMEDIA;
            case "TEXTILE", "TEXTILES" -> TEXTILE;
            case "CNC" -> CNC;
            case "TELECOMS", "TELECOM", "TELECOM_LAB" -> TELECOMS;
            default -> DepartmentCode.valueOf(n);
        };
    }
}

