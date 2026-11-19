package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.dto.training.BootcampAttendance;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.operations.PaymentLinkQuery;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;

/**
 * Builds the bootcamp screen's sessions from training sessions: two queries for the registrations and the
 * invoices however many sessions there are (D-045, D-046).
 */
@Component
public class BootcampView {
    /** Registration number given to someone enrolled without a student number; shown as null again. */
    static final String NO_STUDENT_NUMBER_PREFIX = "EXT-";
    static final String INVOICE_SOURCE = "SESSION";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TrainingParticipantRepository participants;
    private final PaymentLinkQuery payments;
    private final Clock clock;

    public BootcampView(TrainingParticipantRepository participants, PaymentLinkQuery payments, Clock clock) {
        this.participants = participants;
        this.payments = payments;
        this.clock = clock;
    }

    public List<BootcampResponse> views(List<TrainingSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TrainingParticipant>> bySession = participants
                .findActiveBySessionIds(sessions.stream().map(TrainingSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(TrainingParticipant::getSessionId));
        Map<String, PaymentStatus> invoiced = payments.bySource(INVOICE_SOURCE, bySession.values().stream()
                .flatMap(List::stream).map(participant -> participant.getId().toString()).toList());
        return sessions.stream()
                .map(session -> view(session, bySession.getOrDefault(session.getId(), List.of()), invoiced))
                .toList();
    }

    public BootcampResponse view(TrainingSession session) {
        return views(List.of(session)).get(0);
    }

    private BootcampResponse view(
            TrainingSession session, List<TrainingParticipant> enrolled, Map<String, PaymentStatus> invoiced) {
        var starts = session.getStartsAt().atZone(clock.getZone());
        var ends = session.getEndsAt().atZone(clock.getZone());
        return new BootcampResponse(
                session.getId().toString(),
                session.getTitle(),
                session.getFacilitator(),
                starts.toLocalDate().toString(),
                starts.format(TIME),
                ends.format(TIME),
                session.getCapacity(),
                session.getFee(),
                enrolled.stream().map(participant -> participant(session, participant, invoiced)).toList(),
                enrolled.stream()
                        .flatMap(participant -> BootcampAttendance.shown(participant.getAttendance())
                                .map(status -> new BootcampResponse.AttendanceEntry(participant.getId().toString(), status))
                                .stream())
                        .toList());
    }

    private BootcampResponse.Participant participant(
            TrainingSession session, TrainingParticipant participant, Map<String, PaymentStatus> invoiced) {
        String regNumber = participant.getRegNumber();
        return new BootcampResponse.Participant(
                participant.getId().toString(),
                participant.getName(),
                participant.getContactEmail() == null ? "" : participant.getContactEmail(),
                regNumber.startsWith(NO_STUDENT_NUMBER_PREFIX) ? null : regNumber,
                paymentOf(session, participant, invoiced),
                participant.getRegisteredAt().atZone(clock.getZone()).toOffsetDateTime());
    }

    // No invoice: a paid session whose seat was confirmed by hand (F3's confirm-payment) counts as paid (D-046).
    private static PaymentStatus paymentOf(
            TrainingSession session, TrainingParticipant participant, Map<String, PaymentStatus> invoiced) {
        PaymentStatus linked = invoiced.get(participant.getId().toString());
        if (linked != null) {
            return linked;
        }
        return session.getFee() > 0 && participant.getStatus() == RegistrationStatus.CONFIRMED
                ? PaymentStatus.CONFIRMED : PaymentStatus.NOT_REQUESTED;
    }
}
