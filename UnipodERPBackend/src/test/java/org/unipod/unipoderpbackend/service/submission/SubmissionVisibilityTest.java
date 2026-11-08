package org.unipod.unipoderpbackend.service.submission;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionVisibilityTest {
    @Test
    void combinedReviewerAndSectorOfficerRolesUseUnionScope() {
        var authentication = new TestingAuthenticationToken(
                "subject-1",
                "credentials",
                "ROLE_REVIEWER",
                "ROLE_SECTOR_OFFICER"
        );

        assertEquals(
                "REVIEWER_OR_SECTOR_OFFICER",
                SubmissionVisibility.scope(authentication)
        );
    }
}
