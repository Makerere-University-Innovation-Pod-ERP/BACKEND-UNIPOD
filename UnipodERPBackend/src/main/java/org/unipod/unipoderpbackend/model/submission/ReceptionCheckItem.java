package org.unipod.unipoderpbackend.model.submission;

import java.util.Arrays;
import java.util.List;

/**
 * The canonical reception verification lines. The key is stable and is what the
 * API accepts; the label is the human-readable line shown to the receptionist.
 *
 * <p>The four lines apply to every submission category. Category-specific
 * evidence (student number, recommendation letter, service requester) is
 * captured on the brief itself rather than as a separate checklist line.</p>
 */
public enum ReceptionCheckItem {
    IDENTITY_CONFIRMED("Submitter identity confirmed"),
    CONTACT_DETAILS_CONFIRMED("Contact details confirmed"),
    BRIEF_SUMMARY_CLEAR("Brief summary is clear"),
    DOCUMENTS_PRESENT("Required documents present");

    private final String label;

    ReceptionCheckItem(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<ReceptionCheckItem> applicableTo(SubmissionCategory category) {
        return List.of(values());
    }

    public static ReceptionCheckItem fromKey(String key) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown reception checklist item."));
    }
}
