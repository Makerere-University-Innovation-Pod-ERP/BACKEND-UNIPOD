package org.unipod.unipoderpbackend.model.submission;

import java.util.Arrays;
import java.util.List;

/**
 * The canonical reception verification lines. The key is stable and is what the
 * API accepts; the label is the human-readable line shown to the receptionist.
 */
public enum ReceptionCheckItem {
    IDENTITY_CONFIRMED("Submitter identity confirmed", false),
    CONTACT_DETAILS_CONFIRMED("Contact details confirmed", false),
    BRIEF_SUMMARY_CLEAR("Brief summary is clear", false),
    PROBLEM_STATEMENT_CLEAR("Problem statement is clear", false),
    DOCUMENTS_PRESENT("Required documents present", false),
    DUPLICATE_CHECK("No duplicate brief found", false),
    STUDENT_EVIDENCE("Student number, programme and supervisor confirmed", true),
    RECOMMENDATION_LETTER("Recommendation letter status confirmed", true);

    private final String label;
    private final boolean studentOnly;

    ReceptionCheckItem(String label, boolean studentOnly) {
        this.label = label;
        this.studentOnly = studentOnly;
    }

    public String label() {
        return label;
    }

    public boolean studentOnly() {
        return studentOnly;
    }

    public static List<ReceptionCheckItem> applicableTo(SubmissionCategory category) {
        return Arrays.stream(values())
                .filter(item -> !item.studentOnly || category == SubmissionCategory.STUDENT)
                .toList();
    }

    public static ReceptionCheckItem fromKey(String key) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown reception checklist item."));
    }
}
