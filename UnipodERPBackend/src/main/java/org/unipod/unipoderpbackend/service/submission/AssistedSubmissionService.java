package org.unipod.unipoderpbackend.service.submission;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.AssistedAccountResponse;
import org.unipod.unipoderpbackend.dto.submission.AssistedSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.AssistedSubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

@Service
public class AssistedSubmissionService {
    private final AuthenticationService authentication;
    private final SubmissionService submissions;

    public AssistedSubmissionService(
            AuthenticationService authentication,
            SubmissionService submissions
    ) {
        this.authentication = authentication;
        this.submissions = submissions;
    }

    @Transactional
    public AssistedSubmissionResponse create(AssistedSubmissionRequest request, String actor) {
        AuthenticationService.AssistedAccount account =
                authentication.createAssistedSubmitter(request.submitterEmail());
        SubmissionResponse submission = submissions.create(
                new CreateSubmissionRequest(
                        request.category(),
                        request.title(),
                        request.purpose(),
                        request.problemAddressed(),
                        account.email(),
                        request.studentNumber(),
                        request.programme(),
                        request.supervisor(),
                        request.recommendationLetterStatus(),
                        account.email(),
                        null,
                        request.submitterPhone(),
                        request.serviceRequested(),
                        request.serviceRequester(),
                        request.department(),
                        request.expectedResult(),
                        request.preferredAt(),
                        request.acceptedRulesVersion()
                ),
                actor
        );
        return new AssistedSubmissionResponse(
                submission,
                new AssistedAccountResponse(
                        account.email(),
                        account.created(),
                        account.temporaryPassword(),
                        account.setPasswordToken(),
                        account.setPasswordUrl()
                )
        );
    }
}
