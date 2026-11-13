package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.WalkInSubmissionRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.model.auth.AccountKind;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.auth.UserProfile;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalkInSubmissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final String PHONE = "+256700000000";
    private static final String ACTOR = "reception@example.test";

    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final SubmissionService submissions = mock(SubmissionService.class);
    private final ApiUserRepository users = mock(ApiUserRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Authentication actor = mock(Authentication.class);
    private final WalkInSubmissionService service = new WalkInSubmissionService(
            authentication,
            submissions,
            users,
            profiles,
            clock
    );

    @BeforeEach
    void setUp() {
        when(actor.getName()).thenReturn(ACTOR);
    }

    @Test
    void requiresTheRulesVersionTheSubmitterAccepted() {
        InvalidSubmissionException exception = assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request(null, AccountKind.PUBLIC, null), actor)
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains("rules"),
                exception.getMessage()
        );
        verifyNoInteractions(users, profiles, authentication);
    }

    @Test
    void rejectsAPhoneNumberTooShortToMatchAnAccount() {
        WalkInSubmissionRequest request = new WalkInSubmissionRequest(
                "Walk In", "12345", null, AccountKind.PUBLIC,
                SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                null, null, null, null, null, null, null, null, null, "2026-01"
        );

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request, actor)
        );
        verifyNoInteractions(users, profiles, authentication);
    }

    @Test
    void rejectsAPhoneNumberSharedByMoreThanOneAccount() {
        UserProfile first = profile(UUID.randomUUID(), AccountKind.PUBLIC);
        UserProfile second = profile(UUID.randomUUID(), AccountKind.PUBLIC);
        when(profiles.findAllByNormalizedPhone(PHONE)).thenReturn(List.of(first, second));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request(null, AccountKind.PUBLIC, "2026-01"), actor)
        );
    }

    @Test
    void rejectsAnEmailAndPhoneThatBelongToDifferentAccounts() {
        ApiUser emailUser = user("email@example.test");
        when(users.findByEmailIgnoreCase("email@example.test")).thenReturn(Optional.of(emailUser));
        when(profiles.findAllByNormalizedPhone(PHONE)).thenReturn(List.of(profile(UUID.randomUUID(), AccountKind.PUBLIC)));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request("email@example.test", AccountKind.PUBLIC, "2026-01"), actor)
        );
        verifyNoInteractions(authentication);
    }

    @Test
    void rejectsAnEmailThatDoesNotMatchTheAccountForThePhone() {
        ApiUser phoneUser = user("phone@example.test");
        when(profiles.findAllByNormalizedPhone(PHONE)).thenReturn(List.of(profile(phoneUser.getId(), AccountKind.PUBLIC)));
        when(users.findById(phoneUser.getId())).thenReturn(Optional.of(phoneUser));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request("different@example.test", AccountKind.PUBLIC, "2026-01"), actor)
        );
    }

    @Test
    void createsAStudentProfileWithTheDetailsTheSchemaRequires() {
        String email = "student@example.test";
        ApiUser user = user(email);
        when(authentication.createAssistedSubmitter(email))
                .thenReturn(new AuthenticationService.AssistedAccount(email, true, "temporary", "token", "https://portal.example.test/set-password?token=token"));
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(profiles.findById(user.getId())).thenReturn(Optional.empty());
        when(profiles.existsByStudentNumberIgnoreCase("S123")).thenReturn(false);
        SubmissionResponse draft = mock(SubmissionResponse.class);
        when(submissions.create(any(), eq(ACTOR), eq(false))).thenReturn(draft);

        service.submit(request(email, AccountKind.STUDENT, "2026-01"), actor);

        verify(profiles).save(argThat(profile ->
                profile.getAccountKind() == AccountKind.STUDENT
                        && "S123".equals(profile.getStudentNumber())
                        && "BSc Computer Science".equals(profile.getCourse())
                        && email.equals(profile.getUniversityEmail())
                        && PHONE.equals(profile.getPhone())
                        && "2026-01".equals(profile.getRulesVersion())
        ));
    }

    @Test
    void rejectsStudentAccountsWithoutTheProfileDetailsTheSchemaRequires() {
        String email = "student@example.test";
        ApiUser user = user(email);
        when(authentication.createAssistedSubmitter(email))
                .thenReturn(new AuthenticationService.AssistedAccount(email, true, "temporary", "token", "https://portal.example.test/set-password?token=token"));
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(profiles.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(
                        new WalkInSubmissionRequest(
                                "Walk In", PHONE, email, AccountKind.STUDENT,
                                SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                                null, null, null, null, null, null, null, null, null, "2026-01"
                        ),
                        actor
                )
        );
        verify(profiles, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rejectsOrganisationWalkInsThatCannotCarryTheRequiredProfileDetails() {
        String email = "org@example.test";
        ApiUser user = user(email);
        when(authentication.createAssistedSubmitter(email))
                .thenReturn(new AuthenticationService.AssistedAccount(email, true, "temporary", "token", "https://portal.example.test/set-password?token=token"));
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(profiles.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.submit(request(email, AccountKind.ORGANISATION, "2026-01"), actor)
        );
        verifyNoInteractions(submissions);
    }

    private static WalkInSubmissionRequest request(String email, AccountKind kind, String rules) {
        return new WalkInSubmissionRequest(
                "Walk In", PHONE, email, kind,
                SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "S123", "BSc Computer Science", null, null, null, null, null, null, null, rules
        );
    }

    private static UserProfile profile(UUID userId, AccountKind kind) {
        return new UserProfile(userId, kind, "Walk In", "Walk", PHONE, "2026-01", NOW);
    }

    private static ApiUser user(String email) {
        return new ApiUser(UUID.randomUUID(), email, "hash", ApiUserRole.SUBMITTER, NOW);
    }
}
