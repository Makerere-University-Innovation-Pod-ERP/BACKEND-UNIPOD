package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.unipod.unipoderpbackend.model.auth.AccountKind;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

/**
 * A walk-in brief captured by reception. The submitter is identified by the
 * contact details supplied at the desk; an account is created when no account
 * exists for the email address or phone number.
 */
public record WalkInSubmissionRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @JsonProperty("account_kind")
        @JsonAlias("accountKind")
        AccountKind accountKind,
        @NotNull SubmissionCategory category,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 5000) String purpose,
        @NotBlank @Size(max = 5000) String problemAddressed,
        @Size(max = 80) String studentNumber,
        @Size(max = 200) String programme,
        @Size(max = 255) String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        @Size(max = 255) String serviceRequested,
        ServiceRequester serviceRequester,
        @Size(max = 255) String department,
        @Size(max = 5000) String expectedResult,
        Instant preferredAt,
        @Size(max = 32) String acceptedRulesVersion
) {
}
