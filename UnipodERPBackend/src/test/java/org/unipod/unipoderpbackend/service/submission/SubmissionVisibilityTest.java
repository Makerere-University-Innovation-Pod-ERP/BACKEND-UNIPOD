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

    @Test
    void removedIpLiaisonRoleNoLongerSeesEverySubmission() {
        var authentication = new TestingAuthenticationToken(
                "subject-1",
                "credentials",
                "ROLE_IP_LIAISON"
        );

        assertEquals("SUBMITTER", SubmissionVisibility.scope(authentication));
    }

    @Test
    void readOnlyObserverStillUsesTheAllSubmissionsScope() {
        var authentication = new TestingAuthenticationToken(
                "observer-1",
                "credentials",
                "ROLE_OBSERVER"
        );

        assertEquals("ALL", SubmissionVisibility.scope(authentication));
    }
}
