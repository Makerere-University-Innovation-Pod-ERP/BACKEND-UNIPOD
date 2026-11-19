package org.unipod.unipoderpbackend.service.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.validation.Validator;
import java.sql.SQLException;
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
import org.unipod.unipoderpbackend.dto.improvement.SubmitImprovedVersionRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;

/** Constraint and optimistic-lock conversion for answers, and the in-service role check. */
class ImprovementResponseServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"uq_case_revisions_brief_version", "uq_case_revisions_request"})
    void revisionUniqueViolationsBecomeAlreadyAnswered(String constraint) {
        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> ImprovementResponseService.persist(() -> {
                    throw violation(constraint);
                }));
        assertEquals(ImprovementResponseService.ALREADY_ANSWERED, thrown.getMessage());
    }

    @Test
    void optimisticLockFailureBecomesAlreadyAnswered() {
        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> ImprovementResponseService.persist(() -> {
                    throw new ObjectOptimisticLockingFailureException(CaseRevision.class, 2L);
                }));
        assertEquals(ImprovementResponseService.ALREADY_ANSWERED, thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ck_case_revisions_version_number", "fk_case_revisions_request"})
    void otherIntegrityViolationsAreRethrownUnchanged(String constraint) {
        DataIntegrityViolationException other = violation(constraint);
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> ImprovementResponseService.persist(() -> {
                    throw other;
                }));
        assertSame(other, thrown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR", "FACT_FINDING_OFFICER", "REVIEWER"})
    void serviceRejectsStaffBeforeTouchingAnything(String role) {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        SubmissionService submissionService = mock(SubmissionService.class);
        RefinementRequestRepository requests = mock(RefinementRequestRepository.class);
        ImprovementResponseService service = new ImprovementResponseService(submissions, requests,
                mock(RefinementRequestedChangeRepository.class), mock(CaseRevisionRepository.class),
                submissionService, mock(ImprovementAudit.class), mock(Validator.class), java.time.Clock.systemUTC());
        UsernamePasswordAuthenticationToken staff = new UsernamePasswordAuthenticationToken("x@example.test", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        assertThrows(AccessDeniedException.class, () -> service.submit(UUID.randomUUID(),
                new SubmitImprovedVersionRequest("x", 0L, null, null, null, null, null, null, null, null, null, null,
                        null, null), staff));
        assertThrows(AccessDeniedException.class, () -> service.myImprovement(UUID.randomUUID(), staff));
        verifyNoInteractions(submissions, submissionService, requests);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }
}
