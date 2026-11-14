package org.unipod.unipoderpbackend.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.FieldErrorResponse;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.dto.auth.SignupRequest;
import org.unipod.unipoderpbackend.exception.auth.InvalidSignupException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.AccountKind;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.auth.UserProfile;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

@Service
public class SignupService {
    private static final String REQUIRED = "is required";

    private final ApiUserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final AuditService audit;
    private final Clock clock;

    public SignupService(
            ApiUserRepository users,
            UserProfileRepository profiles,
            PasswordEncoder passwordEncoder,
            SessionService sessions,
            AuditService audit,
            Clock clock
    ) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse signUp(SignupRequest request) {
        checkKindDetails(request);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw conflict("An account with this email already exists. Sign in or reset your password.");
        }
        String studentNumber = trimmed(request.studentNumber());
        if (request.accountKind() == AccountKind.STUDENT && profiles.existsByStudentNumberIgnoreCase(studentNumber)) {
            throw conflict("This student number is already registered.");
        }

        Instant now = clock.instant();
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                ApiUserRole.SUBMITTER,
                now
        );
        UserProfile profile = new UserProfile(
                user.getId(),
                request.accountKind(),
                request.fullName().trim(),
                trimmed(request.displayFirstName()),
                request.phone().trim(),
                request.rulesVersion().trim(),
                now
        );
        if (request.accountKind() == AccountKind.STUDENT) {
            profile.recordStudentDetails(
                    studentNumber,
                    trimmed(request.course()),
                    request.universityEmail().trim().toLowerCase(Locale.ROOT)
            );
        }
        if (request.accountKind() == AccountKind.ORGANISATION) {
            profile.recordOrganisationDetails(
                    trimmed(request.organisationName()),
                    trimmed(request.contactPerson()),
                    trimmed(request.mouReference())
            );
        }
        try {
            users.saveAndFlush(user);
            profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("An account with this email or student number already exists.");
        }

        audit.log(AuditEvent.created(
                new Actor(email, ApiUserRole.SUBMITTER, Set.of()),
                "user.signed_up",
                "user",
                user.getId(),
                Map.of("email", email, "accountKind", request.accountKind().name())
        ));
        return sessions.issue(user);
    }

    private static void checkKindDetails(SignupRequest request) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (request.accountKind() == AccountKind.STUDENT) {
            requireText(errors, "studentNumber", request.studentNumber());
            requireText(errors, "course", request.course());
            requireText(errors, "universityEmail", request.universityEmail());
        }
        if (request.accountKind() == AccountKind.ORGANISATION) {
            requireText(errors, "organisationName", request.organisationName());
        }
        if (!errors.isEmpty()) {
            throw new InvalidSignupException(errors);
        }
    }

    private static void requireText(List<FieldErrorResponse> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldErrorResponse(field, REQUIRED));
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
