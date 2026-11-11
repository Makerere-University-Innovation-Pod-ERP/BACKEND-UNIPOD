package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheck;

public record ReceptionCheckItemResponse(
        String key,
        String label,
        boolean checked,
        String note,
        String checkedBy,
        Instant checkedAt,
        Instant updatedAt
) {
    public static ReceptionCheckItemResponse from(ReceptionCheck check) {
        return new ReceptionCheckItemResponse(
                check.getItemKey(),
                check.getLabel(),
                check.isChecked(),
                check.getNote(),
                check.getCheckedBy(),
                check.getCheckedAt(),
                check.getUpdatedAt()
        );
    }
}
