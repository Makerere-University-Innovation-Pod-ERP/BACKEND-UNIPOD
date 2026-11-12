package org.unipod.unipoderpbackend.controller.training;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.training.CreateSessionRequest;
import org.unipod.unipoderpbackend.dto.training.RegisterParticipantRequest;
import org.unipod.unipoderpbackend.dto.training.SessionResponse;
import org.unipod.unipoderpbackend.dto.training.TrainingOverviewResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.training.TrainingQueryService;
import org.unipod.unipoderpbackend.service.training.TrainingSessionService;

@RestController
@RequestMapping("/operations/training")
@Validated
public class TrainingController {
    // Every role except SUBMITTER may read operations data (D-011); OBSERVER is read-only.
    private static final String READ_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER', 'OBSERVER')";
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final TrainingQueryService queries;
    private final TrainingSessionService sessions;
    private final CurrentActor currentActor;

    public TrainingController(
            TrainingQueryService queries,
            TrainingSessionService sessions,
            CurrentActor currentActor) {
        this.queries = queries;
        this.sessions = sessions;
        this.currentActor = currentActor;
    }

    @GetMapping("/overview")
    @PreAuthorize(READ_ROLES)
    public TrainingOverviewResponse overview(@RequestParam(required = false) UUID departmentId) {
        return queries.overview(departmentId);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize(READ_ROLES)
    public SessionResponse session(@PathVariable UUID id) {
        return queries.session(id);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public SessionResponse createSession(
            @Valid @RequestBody CreateSessionRequest request, Authentication authentication) {
        return sessions.create(request, currentActor.from(authentication));
    }

    @PostMapping("/sessions/{id}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public SessionResponse registerParticipant(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterParticipantRequest request,
            Authentication authentication) {
        return sessions.registerParticipant(id, request, currentActor.from(authentication));
    }
}
