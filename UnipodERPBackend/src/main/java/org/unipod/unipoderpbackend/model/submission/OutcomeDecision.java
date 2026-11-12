package org.unipod.unipoderpbackend.model.submission;

import java.util.Locale;
import java.util.Optional;

public enum OutcomeDecision {
    CONFIRMED_AS_PROJECT("Confirmed as project"),
    NEEDS_MORE_IMPROVEMENT("Needs more improvement"),
    CLOSED_WITH_REASON("Closed with a reason");

    private final String label;

    OutcomeDecision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<OutcomeDecision> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (OutcomeDecision decision : values()) {
            if (decision.name().equals(normalized) || decision.label.equalsIgnoreCase(value.trim())) {
                return Optional.of(decision);
            }
        }
        return Optional.empty();
    }
}
