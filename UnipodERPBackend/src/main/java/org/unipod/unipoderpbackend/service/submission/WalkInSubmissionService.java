package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
 * The submitter is matched by email first, then by phone; a minimal account is
 * created when neither is known. The brief is submitted immediately so it
 * appears in the reception queue.
 */
@Service
public class WalkInSubmissionService {
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
        String name = clean(request.name());
        if (name == null) {
            throw new InvalidSubmissionException("The submitter's name is required.");
        }
        String phone = clean(request.phone());
        if (phone == null) {
            throw new InvalidSubmissionException("The submitter's phone number is required.");
        }
        if (phone.replaceAll("[^0-9]", "").length() < 7) {
            throw new InvalidSubmissionException("The submitter's phone number is too short.");
        }
        String email = clean(request.email());
        String acceptedRules = clean(request.acceptedRulesVersion());
        if (acceptedRules == null) {
            throw new InvalidSubmissionException(
                    "Record the version of the rules the submitter accepted before receiving the brief."
            );
        }

        ApiUser user = email == null ? null : users.findByEmailIgnoreCase(email).orElse(null);
        List<UserProfile> phoneProfiles = profiles.findAllByNormalizedPhone(phone);
        UserProfile phoneProfile;
        if (user != null) {
            ApiUser emailUser = user;
            List<UserProfile> userPhoneProfiles = phoneProfiles.stream()
                    .filter(profile -> profile.getUserId().equals(emailUser.getId()))
                    .toList();
            if (!userPhoneProfiles.isEmpty()) {
                // The email identifies the account when several profiles share
                // the phone number.
                phoneProfile = userPhoneProfiles.get(0);
            } else if (!phoneProfiles.isEmpty()) {
                throw new InvalidSubmissionException(
                        "The email address and phone number belong to different accounts. Correct the details and retry."
                );
            } else {
                phoneProfile = null;
            }
        } else {
            if (phoneProfiles.size() > 1) {
                throw new InvalidSubmissionException(
                        "More than one account uses this phone number; capture the submitter's email address to identify them."
                );
            }
            phoneProfile = phoneProfiles.isEmpty() ? null : phoneProfiles.get(0);
            if (phoneProfile != null) {
                user = users.findById(phoneProfile.getUserId())
                        .orElseThrow(() -> new InvalidSubmissionException(
                                "The account registered with this phone number could not be loaded."
                        ));
                if (email != null && !user.getEmail().equalsIgnoreCase(email)) {
                    throw new InvalidSubmissionException(
                            "The email address does not match the account registered with this phone number."
                    );
                }
            }
        }

        AssistedAccountResponse account;
        UserProfile profile;
        if (user == null) {
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
            profile = null;
        } else {
            account = new AssistedAccountResponse(user.getEmail(), false, null, null, null);
            profile = profiles.findById(user.getId()).orElse(null);
        }

        ensureProfile(user, profile, request, name, phone, email, acceptedRules);

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
                name,
                phone,
                clean(request.serviceRequested()),
                request.serviceRequester(),
                clean(request.department()),
                clean(request.expectedResult()),
                request.preferredAt(),
                acceptedRules
        );
        SubmissionResponse draft = submissions.create(create, actor, false);
        SubmissionResponse submitted = submissions.submit(draft.id(), actor, false);
        return new WalkInSubmissionResponse(submitted, account);
    }

    private void ensureProfile(
            ApiUser user,
            UserProfile existing,
            WalkInSubmissionRequest request,
            String name,
            String phone,
            String email,
            String acceptedRules
    ) {
        AccountKind requestedKind = request.accountKind();
        if (existing != null) {
            AccountKind accountKind = requestedKind == null ? existing.getAccountKind() : requestedKind;
            if (accountKind != existing.getAccountKind()) {
                throw new InvalidSubmissionException(
                        "This submitter already has a "
                                + existing.getAccountKind().name().toLowerCase(Locale.ROOT)
                                + " account; a walk-in cannot change the account kind."
                );
            }
            return;
        }

        AccountKind accountKind = requestedKind == null ? AccountKind.PUBLIC : requestedKind;
        Instant now = clock.instant();
        String fullName = name.length() <= 200 ? name : name.substring(0, 200);
        UserProfile profile = new UserProfile(
                user.getId(),
                accountKind,
                fullName,
                firstName(name),
                phone,
                acceptedRules,
                now
        );
        if (accountKind == AccountKind.STUDENT) {
            String studentNumber = clean(request.studentNumber());
            String programme = clean(request.programme());
            if (studentNumber == null || programme == null || email == null) {
                throw new InvalidSubmissionException(
                        "A student account needs a student number, programme and university email address."
                );
            }
            if (profiles.existsByStudentNumberIgnoreCase(studentNumber)) {
                throw new InvalidSubmissionException("This student number is already registered.");
            }
            profile.recordStudentDetails(
                    studentNumber,
                    programme,
                    email.toLowerCase(Locale.ROOT)
            );
        }
        if (accountKind == AccountKind.ORGANISATION) {
            throw new InvalidSubmissionException(
                    "A walk-in cannot create an organisation account; capture the organisation details "
                            + "through signup first."
            );
        }
        profiles.save(profile);
    }

    private static String firstName(String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        String first = space < 0 ? trimmed : trimmed.substring(0, space);
        return first.length() <= 80 ? first : first.substring(0, 80);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
