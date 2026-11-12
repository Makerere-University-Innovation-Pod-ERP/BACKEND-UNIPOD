package org.unipod.unipoderpbackend.service.authorization;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.authorization.AccessDeniedWithReasonException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationDecision;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.model.authorization.DenialCode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationServiceTest {
    private static final UUID INNOVATION = UUID.randomUUID();
    private static final UUID TEXTILE = UUID.randomUUID();

    private final AuthorizationService service = new AuthorizationService(new AuthorizationPolicy());

    @Test
    void administratorMayDoEverythingOnAnyRecord() {
        Actor admin = actor("admin@unipod.ac.ug", ApiUserRole.ADMINISTRATOR);

        for (AuthorizationAction action : AuthorizationAction.values()) {
            assertTrue(service.check(admin, action, submission(null, Set.of(), null)).allowed(), action.name());
        }
    }

    @Test
    void roleWithoutTheActionIsDenied() {
        Actor receptionist = actor("desk@unipod.ac.ug", ApiUserRole.RECEPTIONIST);

        AuthorizationDecision decision = service.check(receptionist, AuthorizationAction.CASE_DECIDE,
                submission(null, Set.of(), null));

        assertFalse(decision.allowed());
        assertEquals(DenialCode.ROLE_NOT_PERMITTED, decision.code());
        assertEquals(DenialCode.ROLE_NOT_PERMITTED.message(), decision.reason());
    }

    @Test
    void submitterMayViewOwnSubmissionIgnoringEmailCase() {
        Actor submitter = actor("Student@Mak.ac.ug ", ApiUserRole.SUBMITTER);

        assertTrue(service.check(submitter, AuthorizationAction.CASE_VIEW,
                submission("student@mak.ac.ug", Set.of(), null)).allowed());
    }

    @Test
    void submitterMayNotViewSomeoneElsesSubmission() {
        Actor submitter = actor("student@mak.ac.ug", ApiUserRole.SUBMITTER);

        AuthorizationDecision decision = service.check(submitter, AuthorizationAction.CASE_VIEW,
                submission("other@mak.ac.ug", Set.of(), null));

        assertEquals(DenialCode.NOT_OWNER, decision.code());
    }

    @Test
    void reviewerMayReviewOnlyAssignedSubmissions() {
        Actor reviewer = actor("faith@unipod.ac.ug", ApiUserRole.REVIEWER);

        assertTrue(service.check(reviewer, AuthorizationAction.CASE_REVIEW,
                submission(null, Set.of("FAITH@unipod.ac.ug", "kaddu@unipod.ac.ug"), null)).allowed());
        assertEquals(DenialCode.NOT_ASSIGNED, service.check(reviewer, AuthorizationAction.CASE_REVIEW,
                submission(null, Set.of("kaddu@unipod.ac.ug"), null)).code());
    }

    @Test
    void departmentStaffSeeOnlyTheirDepartment() {
        Actor officer = new Actor("officer@unipod.ac.ug", ApiUserRole.SECTOR_OFFICER, Set.of(INNOVATION));

        assertTrue(service.check(officer, AuthorizationAction.CASE_VIEW,
                submission(null, Set.of(), INNOVATION)).allowed());
        assertEquals(DenialCode.OUTSIDE_DEPARTMENT, service.check(officer, AuthorizationAction.CASE_VIEW,
                submission(null, Set.of(), TEXTILE)).code());
        assertEquals(DenialCode.OUTSIDE_DEPARTMENT, service.check(officer, AuthorizationAction.CASE_VIEW,
                submission(null, Set.of(), null)).code());
    }

    @Test
    void observerMayReadButNotWrite() {
        Actor observer = actor("observer@unipod.ac.ug", ApiUserRole.OBSERVER);

        assertTrue(service.check(observer, AuthorizationAction.CASE_VIEW, submission(null, Set.of(), null)).allowed());
        assertEquals(DenialCode.ROLE_NOT_PERMITTED, service.check(observer, AuthorizationAction.CASE_EDIT,
                submission(null, Set.of(), null)).code());
    }

    @Test
    void authorizeThrowsWithTheDecisionWhenDenied() {
        Actor submitter = actor("student@mak.ac.ug", ApiUserRole.SUBMITTER);

        AccessDeniedWithReasonException exception = assertThrows(AccessDeniedWithReasonException.class,
                () -> service.authorize(submitter, AuthorizationAction.CASE_EDIT,
                        submission("other@mak.ac.ug", Set.of(), null)));

        assertEquals(DenialCode.NOT_OWNER, exception.getDecision().code());
    }

    @Test
    void authorizeReturnsQuietlyWhenAllowed() {
        Actor submitter = actor("student@mak.ac.ug", ApiUserRole.SUBMITTER);

        assertDoesNotThrow(() -> service.authorize(submitter, AuthorizationAction.CASE_EDIT,
                submission("student@mak.ac.ug", Set.of(), null)));
    }

    private static Actor actor(String email, ApiUserRole role) {
        return new Actor(email, role, Set.of());
    }

    private static AuthorizationResource submission(String owner, Set<String> assignees, UUID department) {
        return new AuthorizationResource("submission", UUID.randomUUID(), owner, assignees, department);
    }
}
