package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.auth.AccountKind;

public record SignupRequest(
        @NotNull AccountKind accountKind,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 80) String displayFirstName,
        @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,20}", message = "must be a valid phone number") String phone,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 40) String studentNumber,
        @Size(max = 200) String course,
        @Email @Size(max = 254) String universityEmail,
        @Size(max = 200) String organisationName,
        @Size(max = 200) String contactPerson,
        @Size(max = 100) String mouReference,
        @AssertTrue(message = "must be accepted") boolean rulesAccepted,
        @NotBlank @Size(max = 40) String rulesVersion
) {
}
