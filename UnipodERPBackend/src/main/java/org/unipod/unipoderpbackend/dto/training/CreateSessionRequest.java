package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The frontend sends {@code date} as the ISO start time. {@code departmentId}, {@code fee} and
 * {@code safetyCourse} are additions the frontend does not send yet (D-021); only the department is required.
 */
public record CreateSessionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String facilitator,
        @NotNull OffsetDateTime date,
        OffsetDateTime endsAt,
        @NotNull @Min(1) Integer capacity,
        @NotBlank @Size(max = 200) String venue,
        @Size(max = 2000) String description,
        @NotNull UUID departmentId,
        @Min(0) Integer fee,
        Boolean safetyCourse,
        Boolean publish) {
}
