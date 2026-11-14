package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The four named reception verification lines. Each line is optional so a
 * receptionist can save progress, but at least one line must be supplied.
 */
public record ReceptionCheckLinesRequest(
        @JsonProperty("details_complete")
        @JsonAlias({"detailsComplete", "DETAILS_COMPLETE"})
        @Valid Line detailsComplete,
        @JsonProperty("documents_complete")
        @JsonAlias({"documentsComplete", "DOCUMENTS_COMPLETE"})
        @Valid Line documentsComplete,
        @JsonProperty("files_readable")
        @JsonAlias({"filesReadable", "FILES_READABLE"})
        @Valid Line filesReadable,
        @JsonProperty("correct_type")
        @JsonAlias({"correctType", "CORRECT_TYPE"})
        @Valid Line correctType
) {
    public record Line(
            @NotNull Boolean checked,
            @Size(max = 2000) String note
    ) {
    }

    public boolean hasAnyLine() {
        return detailsComplete != null
                || documentsComplete != null
                || filesReadable != null
                || correctType != null;
    }
}
