package org.unipod.unipoderpbackend.service.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.improvement.AskForChangesRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;

/** Constraint and optimistic-lock conversion, and the in-service role check. */
class RefinementRequestServiceTest {
    @Test
    void cycleUniqueViolationBecomesAConflict() {
        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> RefinementRequestService.persist(() -> {
                    throw violation("uq_refinement_requests_brief_cycle");
                }));
        assertEquals(RefinementRequestService.CHANGED, thrown.getMessage());
    }

    @Test
    void optimisticLockFailureBecomesAConflict() {
        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> RefinementRequestService.persist(() -> {
                    throw new ObjectOptimisticLockingFailureException(RefinementRequest.class, 3L);
                }));
        assertEquals(RefinementRequestService.CHANGED, thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ck_refinement_requests_cycle", "fk_refinement_requested_changes_request",
            "uq_refinement_requested_changes_request_position"})
    void otherIntegrityViolationsAreRethrownUnchanged(String constraint) {
        DataIntegrityViolationException other = violation(constraint);
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> RefinementRequestService.persist(() -> {
                    throw other;
                }));
        assertSame(other, thrown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVIEWER", "SUBMITTER", "OBSERVER"})
    void serviceRejectsOtherRolesBeforeTouchingAnything(String role) {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        RefinementRequestRepository requests = mock(RefinementRequestRepository.class);
        CaseFileService caseFiles = mock(CaseFileService.class);
        RefinementRequestService service = new RefinementRequestService(submissions, requests,
                mock(RefinementRequestedChangeRepository.class), caseFiles, mock(ImprovementAudit.class),
                mock(NotificationOutbox.class), java.time.Clock.systemUTC());

        assertThrows(AccessDeniedException.class, () -> service.ask(UUID.randomUUID(),
                new AskForChangesRequest("x", LocalDate.now().plusDays(3), List.of("x"), 0L),
                new UsernamePasswordAuthenticationToken("x@example.test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
        verifyNoInteractions(submissions, requests, caseFiles);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }
}
