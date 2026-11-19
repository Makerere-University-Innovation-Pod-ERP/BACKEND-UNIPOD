package org.unipod.unipoderpbackend.service.training;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.EnrollBootcampRequest;
import org.unipod.unipoderpbackend.dto.training.RegisterParticipantRequest;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Enrols someone in a bootcamp by registering them for the training session, so the session row lock,
 * the seat count, the duplicate check and the audit entry of training registration all apply (D-045).
 */
@Service
public class BootcampEnrolService {
    // Training requires a department text for every registration; the bootcamp form has none.
    private static final String DEPARTMENT_NOT_GIVEN = "Not given";

    private final TrainingSessionService training;
    private final TrainingSessionRepository sessions;
    private final BootcampView view;
    private final DepartmentAccess access;

    public BootcampEnrolService(
            TrainingSessionService training,
            TrainingSessionRepository sessions,
            BootcampView view,
            DepartmentAccess access) {
        this.training = training;
        this.sessions = sessions;
        this.view = view;
        this.access = access;
    }

    @Transactional
    public BootcampResponse enroll(UUID sessionId, EnrollBootcampRequest request, Actor actor) {
        access.requireManage(actor, DepartmentCode.TELECOMS);
        UUID telecoms = access.requireSector(DepartmentCode.TELECOMS);
        sessions.findById(sessionId)
                .filter(session -> telecoms.equals(session.getDepartmentId()))
                .orElseThrow(TrainingNotFoundException::session);
        training.registerParticipant(sessionId, new RegisterParticipantRequest(
                request.name(), registrationNumber(request.studentNumber()), DEPARTMENT_NOT_GIVEN, request.email()),
                actor);
        TrainingSession session = sessions.findById(sessionId).orElseThrow(TrainingNotFoundException::session);
        return view.view(session);
    }

    private static String registrationNumber(String studentNumber) {
        if (studentNumber != null && !studentNumber.isBlank()) {
            return studentNumber.strip();
        }
        return BootcampView.NO_STUDENT_NUMBER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }
}
