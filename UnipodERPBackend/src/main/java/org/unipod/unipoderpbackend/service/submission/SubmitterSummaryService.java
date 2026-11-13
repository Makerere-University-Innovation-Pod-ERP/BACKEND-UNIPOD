package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.SubmissionsSummaryResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmissionsSummaryResponse.TabCount;
import org.unipod.unipoderpbackend.dto.submission.SubmissionsSummaryResponse.WaitingBanner;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.booking.BookingRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;

/**
 * Counts for the submitter's tab bar and the "waiting for you" banners that
 * surface missing documents, improvement requests, unpaid invoices and upcoming
 * bookings or training sessions.
 */
@Service
public class SubmitterSummaryService {
    private final SubmissionRepository submissions;
    private final MissingItemRepository missingItems;
    private final InvoiceRepository invoices;
    private final BookingRepository bookings;
    private final TrainingParticipantRepository trainingParticipants;
    private final Clock clock;

    public SubmitterSummaryService(
            SubmissionRepository submissions,
            MissingItemRepository missingItems,
            InvoiceRepository invoices,
            BookingRepository bookings,
            TrainingParticipantRepository trainingParticipants,
            Clock clock
    ) {
        this.submissions = submissions;
        this.missingItems = missingItems;
        this.invoices = invoices;
        this.bookings = bookings;
        this.trainingParticipants = trainingParticipants;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubmissionsSummaryResponse summary(String submitterId) {
        long all = submissions.countBySubmitterId(submitterId);
        long briefs = submissions.countBySubmitterIdAndCategoryNot(
                submitterId,
                SubmissionCategory.SERVICE_REQUEST
        );
        long serviceRequests = submissions.countBySubmitterIdAndCategory(
                submitterId,
                SubmissionCategory.SERVICE_REQUEST
        );
        long drafts = submissions.countBySubmitterIdAndStage(submitterId, SubmissionStage.DRAFT);
        long awaitingInformation = submissions.countBySubmitterIdAndStage(
                submitterId,
                SubmissionStage.AWAITING_INFORMATION
        );
        long improvements = submissions.countBySubmitterIdAndStage(
                submitterId,
                SubmissionStage.IMPROVEMENT_REQUESTED
        );
        long actionRequired = awaitingInformation + improvements;

        List<TabCount> tabs = List.of(
                new TabCount("ALL", "All", all),
                new TabCount("BRIEFS", "Briefs", briefs),
                new TabCount("SERVICE_REQUESTS", "Service requests", serviceRequests),
                new TabCount("DRAFTS", "Drafts", drafts),
                new TabCount("ACTION_REQUIRED", "Action required", actionRequired)
        );

        Instant now = clock.instant();
        long missingDocumentBriefs = missingItems.countSubmissionsWithOpenItems(
                submitterId,
                MissingItemStatus.OPEN
        );
        long unpaidInvoices = invoices.countByCustomerEmailIgnoreCaseAndStatusIn(
                submitterId,
                List.of(InvoiceStatus.WAITING_FOR_PROOF, InvoiceStatus.WAITING_FOR_CHECK)
        );
        long upcomingBookings = bookings.countUpcoming(
                submitterId,
                now,
                List.of(BookingStatus.PENDING, BookingStatus.APPROVED)
        );
        long upcomingSessions = trainingParticipants.countUpcomingSessionsForEmail(submitterId, now);

        List<WaitingBanner> banners = new ArrayList<>();
        addBanner(
                banners,
                "MISSING_DOCUMENTS",
                "Reception needs more information",
                missingDocumentBriefs == 1
                        ? "1 brief is waiting for the documents or details reception requested."
                        : missingDocumentBriefs + " briefs are waiting for the documents or details reception requested.",
                missingDocumentBriefs,
                "/my-submissions?tab=ACTION_REQUIRED"
        );
        addBanner(
                banners,
                "IMPROVEMENT_REQUEST",
                "An improvement request is waiting for you",
                improvements == 1
                        ? "1 brief needs an updated version before review can continue."
                        : improvements + " briefs need an updated version before review can continue.",
                improvements,
                "/my-submissions?tab=ACTION_REQUIRED"
        );
        addBanner(
                banners,
                "UNPAID_INVOICE",
                "You have an unpaid invoice",
                unpaidInvoices == 1
                        ? "1 invoice is waiting for payment or proof of payment."
                        : unpaidInvoices + " invoices are waiting for payment or proof of payment.",
                unpaidInvoices,
                "/invoices"
        );
        addBanner(
                banners,
                "UPCOMING_BOOKING",
                "You have an upcoming booking",
                upcomingBookings == 1
                        ? "1 facility booking is coming up."
                        : upcomingBookings + " facility bookings are coming up.",
                upcomingBookings,
                "/bookings"
        );
        addBanner(
                banners,
                "UPCOMING_SESSION",
                "You have an upcoming training session",
                upcomingSessions == 1
                        ? "1 registered training session is coming up."
                        : upcomingSessions + " registered training sessions are coming up.",
                upcomingSessions,
                "/training"
        );
        return new SubmissionsSummaryResponse(tabs, banners);
    }

    private static void addBanner(
            List<WaitingBanner> banners,
            String type,
            String title,
            String message,
            long count,
            String href
    ) {
        if (count > 0) {
            banners.add(new WaitingBanner(type, title, message, count, href));
        }
    }
}
