package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.AssistedAccountResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.WalkInSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.WalkInSubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.model.auth.AccountKind;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.UserProfile;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

/**
 * Reception-assisted intake for a submitter who walks in without an account.
 * The submitter is matched by email first, then by phone; a minimal PUBLIC
 * account is created when neither is known. The brief is submitted immediately
 * so it appears in the reception queue.
 */
@Service
public class WalkInSubmissionService {
    private static final String WALK_IN_RULES_VERSION = "walk-in";

    private final AuthenticationService authentication;
    private final SubmissionService submissions;
    private final ApiUserRepository users;
    private final UserProfileRepository profiles;
    private final Clock clock;

    public WalkInSubmissionService(
            AuthenticationService authentication,
            SubmissionService submissions,
            ApiUserRepository users,
            UserProfileRepository profiles,
            Clock clock
    ) {
        this.authentication = authentication;
        this.submissions = submissions;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public WalkInSubmissionResponse submit(WalkInSubmissionRequest request, Authentication authenticationContext) {
        String actor = authenticationContext.getName();
        String email = clean(request.email());
        ApiUser user = email == null ? null : users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = findByPhone(request.phone()).orElse(null);
        }

        AssistedAccountResponse account;
        if (user != null) {
            account = new AssistedAccountResponse(user.getEmail(), false, null, null, null);
        } else {
            if (email == null) {
                throw new InvalidSubmissionException(
                        "Provide an email address so an account can be created for this submitter."
                );
            }
            AuthenticationService.AssistedAccount created = authentication.createAssistedSubmitter(email);
            user = users.findByEmailIgnoreCase(created.email())
                    .orElseThrow(() -> new InvalidSubmissionException(
                            "The submitter account could not be created."
                    ));
            account = new AssistedAccountResponse(
                    created.email(),
                    created.created(),
                    created.temporaryPassword(),
                    created.setPasswordToken(),
                    created.setPasswordUrl()
            );
        }

        ensureProfile(user, request);
        String acceptedRules = clean(request.acceptedRulesVersion());
        CreateSubmissionRequest create = new CreateSubmissionRequest(
                request.category(),
                request.title().trim(),
                request.purpose().trim(),
                request.problemAddressed().trim(),
                user.getEmail(),
                clean(request.studentNumber()),
                clean(request.programme()),
                clean(request.supervisor()),
                request.recommendationLetterStatus(),
                user.getEmail(),
                clean(request.name()),
                clean(request.phone()),
                clean(request.serviceRequested()),
                request.serviceRequester(),
                clean(request.department()),
                clean(request.expectedResult()),
                request.preferredAt(),
                acceptedRules == null ? WALK_IN_RULES_VERSION : acceptedRules
        );
        SubmissionResponse draft = submissions.create(create, actor, false);
        SubmissionResponse submitted = submissions.submit(draft.id(), actor, false);
        return new WalkInSubmissionResponse(submitted, account);
    }

    private Optional<ApiUser> findByPhone(String phone) {
        String cleanPhone = clean(phone);
        if (cleanPhone == null) {
            return Optional.empty();
        }
        return profiles.findFirstByPhone(cleanPhone)
                .map(UserProfile::getUserId)
                .flatMap(users::findById);
    }

    private void ensureProfile(ApiUser user, WalkInSubmissionRequest request) {
        if (profiles.findById(user.getId()).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        String name = clean(request.name());
        String fullName = name == null
                ? user.getEmail()
                : name.length() <= 200 ? name : name.substring(0, 200);
        profiles.save(new UserProfile(
                user.getId(),
                request.accountKind() == null ? AccountKind.PUBLIC : request.accountKind(),
                fullName,
                firstName(name),
                clean(request.phone()) == null ? "not-provided" : clean(request.phone()),
                WALK_IN_RULES_VERSION,
                now
        ));
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        String first = space < 0 ? trimmed : trimmed.substring(0, space);
        return first.length() <= 80 ? first : first.substring(0, 80);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
