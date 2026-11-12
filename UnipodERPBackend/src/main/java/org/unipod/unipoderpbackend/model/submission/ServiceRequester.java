package org.unipod.unipoderpbackend.model.submission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Who is asking for a service (prototyping, testing, mentorship, IP support and
 * similar). Service requests use the {@code SERVICE_REQUEST} submission category.
 */
public enum ServiceRequester {
    STUDENT,
    STAFF,
    EXTERNAL,
    OTHER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ServiceRequester fromJson(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
