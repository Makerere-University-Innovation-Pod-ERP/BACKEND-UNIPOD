package org.unipod.unipoderpbackend.dto.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.review.ReviewMode;

// Unknown properties are ignored so a client cannot supply round, setBy or other server-owned fields.
// Field rules are checked in ReviewCommandService and reported as 422 naming the field.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveReviewSettingsRequest(
        ReviewMode mode,
        Integer reviewsNeeded,
        LocalDate dueDate,
        Long version
) {
}
