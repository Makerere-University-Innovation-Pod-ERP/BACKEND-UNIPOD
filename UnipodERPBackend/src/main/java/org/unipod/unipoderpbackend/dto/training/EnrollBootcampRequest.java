package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The enrol form: {@code studentNumber} may be blank for someone outside the university. */
public record EnrollBootcampRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 50) String studentNumber) {
}
