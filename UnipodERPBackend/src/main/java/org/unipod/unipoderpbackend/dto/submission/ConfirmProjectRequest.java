package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Size;

/**
 * Confirms an intake brief as a project. The project code is optional; when it
 * is blank the server allocates the next {@code PRJ-<year>-<sequence>} reference.
 */
public record ConfirmProjectRequest(
        @Size(max = 32) String projectCode,
        @Size(max = 2000) String note
) {
    public ConfirmProjectRequest(String projectCode) {
        this(projectCode, null);
    }
}
