package org.unipod.unipoderpbackend.dto.submission;

import java.util.Locale;

public enum SubmitterTab {
    ALL,
    BRIEFS,
    SERVICE_REQUESTS,
    DRAFTS,
    ACTION_REQUIRED;

    public static SubmitterTab from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
