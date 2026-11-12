package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.unipod.unipoderpbackend.dto.submission.MissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsRequest;
import org.unipod.unipoderpbackend.dto.submission.ResolveMissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.SendReminderRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissingItemServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");
    private static final String ACTOR = "receptionist-1";

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final MissingItemRepository items = mock(MissingItemRepository.class);
    private final NotificationOutbox notificationOutbox = mock(NotificationOutbox.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private MissingItemService service;

    @BeforeEach
    void setUp() {
        service = new MissingItemService(submissions, items, notificationOutbox, clock);
    }

    @Test
    void listCountsOnlyOpenItems() {
        Submission submission = submission("submitter@example.test");
        MissingItem open = MissingItem.information(
                UUID.randomUUID(), submission.getId(), "Project brief", null, ACTOR, NOW
        );
        MissingItem resolved = MissingItem.information(
                UUID.randomUUID(), submission.getId(), "Supervisor name", null, ACTOR, NOW
        );
        resolved.resolve(ACTOR, "Received", NOW.plusSeconds(60));
        when(submissions.findVisibleById(any(), any(), any())).thenReturn(Optional.of(submission));
        when(items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submission.getId()))
                .thenReturn(List.of(open, resolved));

        var response = service.list(submission.getId(), authentication(ACTOR));

        assertEquals(1, response.openCount());
        assertEquals(2, response.items().size());
    }

    @Test
    void addMissingDocumentsCreatesOpenDocumentRequests() {
        Submission submission = submission("submitter@example.test");
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));
        java.util.concurrent.atomic.AtomicReference<MissingItem> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(items.save(any(MissingItem.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submission.getId()))
                .thenAnswer(invocation -> saved.get() == null ? List.of() : List.of(saved.get()));

        var response = service.addMissingDocuments(
                submission.getId(),
                new MissingItemsRequest(List.of(new MissingItemRequest(
                        "Signed recommendation letter",
                        "Please upload the signed copy",
                        DocumentType.RECOMMENDATION_LETTER
                ))),
                authentication(ACTOR)
        );

        MissingItem item = saved.get();
        assertNotNull(item);
        assertEquals(MissingItemKind.DOCUMENT, item.getKind());
        assertEquals(MissingItemStatus.OPEN, item.getStatus());
        assertEquals(DocumentType.RECOMMENDATION_LETTER, item.getDocumentType());
        assertEquals("Signed recommendation letter", item.getLabel());
        assertEquals(1, response.openCount());
        assertEquals(item.getId(), response.items().getFirst().id());
        verify(items).flush();
    }

    @Test
    void addMissingItemsRejectsNullElements() {
        Submission submission = submission("submitter@example.test");
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.addMissingItems(
                        submission.getId(),
                        new MissingItemsRequest(java.util.Collections.singletonList(null)),
                        authentication(ACTOR)
                )
        );
    }

    @Test
    void closedSubmissionRejectsNewMissingItems() {
        Submission submission = submission("submitter@example.test");
        submission.register("GEN-2026-000001", NOW, "receptionist-1");
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.REGISTERED, NOW);
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.FACT_FINDING, NOW);
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.UNDER_REVIEW, NOW);
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.REFINEMENT_REQUESTED, NOW);
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.FURTHER_HANDLING, NOW);
        submission.transitionTo(org.unipod.unipoderpbackend.model.submission.SubmissionStage.COMPLETED, NOW);
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));

        assertThrows(
                SubmissionConflictException.class,
                () -> service.addMissingItems(
                        submission.getId(),
                        new MissingItemsRequest(List.of(new MissingItemRequest("Project brief", null, null))),
                        authentication(ACTOR)
                )
        );
    }

    @Test
    void addMissingDocumentsRequiresDocumentType() {
        Submission submission = submission("submitter@example.test");
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.addMissingDocuments(
                        submission.getId(),
                        new MissingItemsRequest(List.of(new MissingItemRequest("Project brief", null, null))),
                        authentication(ACTOR)
                )
        );
    }

    @Test
    void sendReminderMarksOpenItemsAndWritesOutboxEvent() {
        Submission submission = submission("submitter@example.test");
        MissingItem open = MissingItem.document(
                UUID.randomUUID(),
                submission.getId(),
                "Signed recommendation letter",
                null,
                DocumentType.RECOMMENDATION_LETTER,
                ACTOR,
                NOW
        );
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));
        when(items.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                submission.getId(),
                MissingItemStatus.OPEN
        )).thenReturn(List.of(open));
        when(items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submission.getId()))
                .thenReturn(List.of(open));

        var response = service.sendReminder(
                submission.getId(),
                new SendReminderRequest(null),
                authentication(ACTOR)
        );

        assertEquals(NOW, open.getLastRemindedAt());
        assertEquals(1, response.openCount());
        verify(notificationOutbox).recordReminderSent(
                eq(submission.getId()),
                eq(submission.getCode()),
                eq("submitter@example.test"),
                eq("Signed recommendation letter"),
                eq(ACTOR),
                eq(NOW)
        );
    }

    @Test
    void sendReminderRejectsWhenNoOpenItemsExist() {
        Submission submission = submission("submitter@example.test");
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));
        when(items.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                submission.getId(),
                MissingItemStatus.OPEN
        )).thenReturn(List.of());

        assertThrows(
                SubmissionConflictException.class,
                () -> service.sendReminder(submission.getId(), new SendReminderRequest(null), authentication(ACTOR))
        );
    }

    @Test
    void sendReminderRequiresSubmitterEmail() {
        Submission submission = submission(null);
        MissingItem open = MissingItem.information(
                UUID.randomUUID(), submission.getId(), "Project brief", null, ACTOR, NOW
        );
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));
        when(items.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                submission.getId(),
                MissingItemStatus.OPEN
        )).thenReturn(List.of(open));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.sendReminder(submission.getId(), new SendReminderRequest(null), authentication(ACTOR))
        );
    }

    @Test
    void resolveRecordsResolverAndNote() {
        Submission submission = submission("submitter@example.test");
        MissingItem item = MissingItem.information(
                UUID.randomUUID(), submission.getId(), "Project brief", null, ACTOR, NOW
        );
        when(submissions.findVisibleByIdForUpdate(any(), any(), any())).thenReturn(Optional.of(submission));
        when(items.findByIdAndSubmissionId(item.getId(), submission.getId()))
                .thenReturn(Optional.of(item));

        var response = service.resolve(
                submission.getId(),
                item.getId(),
                new ResolveMissingItemRequest("Received by email"),
                authentication("officer-2")
        );

        assertEquals(MissingItemStatus.RESOLVED, response.status());
        assertEquals("officer-2", response.resolvedBy());
        assertEquals("Received by email", response.resolutionNote());
        assertEquals(NOW, response.resolvedAt());
        assertNotNull(item.getResolvedAt());
    }

    private static TestingAuthenticationToken authentication(String name) {
        return new TestingAuthenticationToken(name, "n/a", "ROLE_RECEPTIONIST");
    }

    private static Submission submission(String submitterEmail) {
        return new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Prototype",
                "Test a prototype",
                "Limited access to equipment",
                "staff-1",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                submitterEmail,
                NOW
        );
    }
}
