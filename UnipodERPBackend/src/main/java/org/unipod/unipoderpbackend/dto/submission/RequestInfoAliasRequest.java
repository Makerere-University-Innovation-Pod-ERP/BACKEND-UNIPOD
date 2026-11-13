package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Reception's request for more information, using the R3 frontend shape:
 * a list of missing item labels, an optional reason, a due date and a
 * submitter-facing message.
 */
public record RequestInfoAliasRequest(
        @JsonProperty("missing_items")
        @JsonAlias({"missingItems"})
        @Size(max = 50, message = "must contain at most 50 items")
        List<@Size(max = 255) String> missingItems,
        @Size(max = 255) String reason,
        @JsonProperty("due_date")
        @JsonAlias({"dueDate"})
        LocalDate dueDate,
        @Size(max = 2000) String message
) {
}
