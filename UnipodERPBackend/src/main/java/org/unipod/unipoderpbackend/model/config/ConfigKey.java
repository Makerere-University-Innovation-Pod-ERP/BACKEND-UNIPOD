package org.unipod.unipoderpbackend.model.config;

public enum ConfigKey {
    CASE_CODE_STUDENT_PREFIX("case.code.student_prefix", "\"STU\""),
    CASE_CODE_GENERAL_PREFIX("case.code.general_prefix", "\"GEN\""),
    CASE_CODE_SEQUENCE_DIGITS("case.code.sequence_digits", "6"),
    REVIEW_DEFAULT_MODE("review.default_mode", "\"PARALLEL\""),
    REVIEW_DEFAULT_REQUIRED_REVIEWS("review.default_required_reviews", "2"),
    RECOMMENDATION_LETTER_REQUIRED_FOR("documents.recommendation_letter_required_for", "[\"STUDENT\"]");

    private final String key;
    private final String defaultJson;

    ConfigKey(String key, String defaultJson) {
        this.key = key;
        this.defaultJson = defaultJson;
    }

    public String key() {
        return key;
    }

    public String defaultJson() {
        return defaultJson;
    }
}
