package org.unipod.unipoderpbackend.model.submission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum RecommendationLetterStatus {
    VERIFIED,
    SUBMITTED,
    ONE_FILE,
    NOT_REQUIRED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static RecommendationLetterStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
