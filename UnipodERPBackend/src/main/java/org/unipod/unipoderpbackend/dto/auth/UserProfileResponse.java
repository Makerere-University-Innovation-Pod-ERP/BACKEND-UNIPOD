package org.unipod.unipoderpbackend.dto.auth;

import org.unipod.unipoderpbackend.model.auth.AccountKind;
import org.unipod.unipoderpbackend.model.auth.UserProfile;

public record UserProfileResponse(
        AccountKind accountKind,
        String fullName,
        String displayFirstName,
        String phone,
        String studentNumber,
        String course,
        String universityEmail,
        String organisationName,
        String contactPerson,
        String mouReference,
        String rulesVersion
) {
    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getAccountKind(),
                profile.getFullName(),
                profile.getDisplayFirstName(),
                profile.getPhone(),
                profile.getStudentNumber(),
                profile.getCourse(),
                profile.getUniversityEmail(),
                profile.getOrganisationName(),
                profile.getContactPerson(),
                profile.getMouReference(),
                profile.getRulesVersion()
        );
    }
}
