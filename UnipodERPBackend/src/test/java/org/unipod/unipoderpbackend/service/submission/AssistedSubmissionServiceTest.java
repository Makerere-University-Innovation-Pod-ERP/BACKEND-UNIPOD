package org.unipod.unipoderpbackend.service.submission;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unipod.unipoderpbackend.dto.submission.AssistedSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistedSubmissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");
    private static final String ACTOR = "receptionist-1";

    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final SubmissionService submissions = mock(SubmissionService.class);

    private final AssistedSubmissionService service =
            new AssistedSubmissionService(authentication, submissions);

    @Test
    void createUsesAssistedAccountAndReturnsOneTimePassword() {
        when(authentication.createAssistedSubmitter("walkin@example.test"))
                .thenReturn(new AuthenticationService.AssistedAccount(
                        "walkin@example.test",
                        true,
                        "TEMP-PASSWORD-123"
                ));
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Walk-in idea",
                "Improve access",
                "Current access is limited",
                ACTOR,
                "walkin@example.test",
                null,
                null,
                null,
                null,
                "walkin@example.test",
                NOW
        );
        when(submissions.create(any(CreateSubmissionRequest.class), eq(ACTOR)))
                .thenReturn(SubmissionResponse.from(submission));

        var response = service.create(
                new AssistedSubmissionRequest(
                        "walkin@example.test",
                        SubmissionCategory.GENERAL,
                        "Walk-in idea",
                        "Improve access",
                        "Current access is limited",
                        null,
                        null,
                        null,
                        null
                ),
                ACTOR
        );

        assertEquals("walkin@example.test", response.account().email());
        assertTrue(response.account().created());
        assertEquals("TEMP-PASSWORD-123", response.account().temporaryPassword());
        assertEquals(submission.getId(), response.submission().id());

        ArgumentCaptor<CreateSubmissionRequest> captured =
                ArgumentCaptor.forClass(CreateSubmissionRequest.class);
        verify(submissions).create(captured.capture(), eq(ACTOR));
        assertEquals("walkin@example.test", captured.getValue().submitterId());
        assertEquals("walkin@example.test", captured.getValue().submitterEmail());
    }
}
