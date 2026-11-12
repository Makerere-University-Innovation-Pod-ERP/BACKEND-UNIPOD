package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Size;

public record SendReminderRequest(
        @Size(max = 2000) String note
) {
}
