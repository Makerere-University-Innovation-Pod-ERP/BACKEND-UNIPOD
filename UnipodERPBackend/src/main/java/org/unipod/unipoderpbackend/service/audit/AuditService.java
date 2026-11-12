package org.unipod.unipoderpbackend.service.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.audit.ActivityFilter;
import org.unipod.unipoderpbackend.dto.audit.AuditEntryResponse;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * audit.log() from the doc (section 5): an append-only "who did what, when" line,
 * plus the two read views (the AD2 audit log and the history panel on a brief).
 */
@Service
public class AuditService {
    static final String SUBMISSION = "submission";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Instant BEGINNING = Instant.EPOCH;
    private static final Instant END_OF_TIME = Instant.parse("9999-12-31T00:00:00Z");
    private static final AuthorizationResource AUDIT_LOG =
            new AuthorizationResource("audit_log", null, null, Set.of(), null);

    private final AuditEntryRepository entries;
    private final SubmissionRepository submissions;
    private final AuthorizationService authorization;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(
            AuditEntryRepository entries,
            SubmissionRepository submissions,
            AuthorizationService authorization,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.entries = entries;
        this.submissions = submissions;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Writes one audit line inside the caller's transaction.
     *
     * <p>MANDATORY means: if there is no transaction already running, fail. So the
     * audit line and the change it describes are saved together or not at all.
     * There can never be a change without its audit line.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(AuditEvent event) {
        entries.save(new AuditEntry(
                UUID.randomUUID(),
                clock.instant(),
                event.actor().email(),
                event.actor().role().name(),
                event.action(),
                event.entityType(),
                event.entityId(),
                toJson(event.before()),
                toJson(event.after()),
                event.departmentId(),
                currentRequestId()
        ));
    }

    /** GET /activity: the whole audit log, filtered. Only roles with AUDIT_VIEW may read it. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(
            Actor actor,
            ActivityFilter filter,
            int page,
            int size
    ) {
        authorization.authorize(actor, AuthorizationAction.AUDIT_VIEW, AUDIT_LOG);
        ZoneId zone = clock.getZone();
        Instant from = filter.from() == null ? BEGINNING : filter.from().atStartOfDay(zone).toInstant();
        Instant to = filter.to() == null ? END_OF_TIME : filter.to().plusDays(1).atStartOfDay(zone).toInstant();
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'.");
        }
        return PageResponse.from(entries.search(
                normaliseEmail(filter.actor()),
                blankToNull(filter.action()),
                blankToNull(filter.entityType()),
                filter.entityId(),
                from,
                to,
                PageRequest.of(page, size)
        ).map(this::toResponse));
    }

    /**
     * GET /cases/{id}/activity: the history panel on a brief.
     * If you can open the brief you can see its history, so we reuse the brief's own
     * visibility rules. A brief you cannot see is a 404, so its existence is not leaked.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> caseActivity(
            UUID caseId,
            Authentication authentication,
            int page,
            int size
    ) {
        submissions.findVisibleById(caseId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(caseId));
        return PageResponse.from(entries.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
                SUBMISSION,
                caseId,
                PageRequest.of(page, size)
        ).map(this::toResponse));
    }

    private AuditEntryResponse toResponse(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getActor(),
                entry.getActorRole(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                fromJson(entry.getBeforeValue()),
                fromJson(entry.getAfterValue()),
                entry.getDepartmentId(),
                entry.getRequestId()
        );
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private JsonNode fromJson(String json) {
        return json == null ? null : objectMapper.readTree(json);
    }

    private static String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            String header = request.getHeader("X-Request-Id");
            if (header != null && SAFE_REQUEST_ID.matcher(header).matches()) {
                return header;
            }
        }
        return null;
    }

    private static String normaliseEmail(String email) {
        String value = blankToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
