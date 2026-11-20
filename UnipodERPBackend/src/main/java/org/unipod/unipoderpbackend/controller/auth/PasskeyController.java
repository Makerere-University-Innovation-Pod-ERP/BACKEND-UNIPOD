package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.dto.auth.PasskeyChallengeResponse;
import org.unipod.unipoderpbackend.dto.auth.PasskeyFinishRequest;
import org.unipod.unipoderpbackend.dto.auth.PasskeyRegistrationFinishRequest;
import org.unipod.unipoderpbackend.dto.auth.PasskeyRegistrationOptionsRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.service.auth.PasskeyService;

@RestController
@RequestMapping("/auth/passkeys")
public class PasskeyController {
    private final PasskeyService passkeys;
    private final ApiUserRepository users;

    public PasskeyController(PasskeyService passkeys, ApiUserRepository users) {
        this.passkeys = passkeys;
        this.users = users;
    }

    @PostMapping("/registration/options")
    @PreAuthorize("isAuthenticated()")
    public PasskeyChallengeResponse registrationOptions(
            @Valid @RequestBody PasskeyRegistrationOptionsRequest request,
            Authentication authentication
    ) {
        return passkeys.startRegistration(requireUser(authentication), request.password());
    }

    @PostMapping("/registration/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void verifyRegistration(
            @Valid @RequestBody PasskeyRegistrationFinishRequest request,
            Authentication authentication
    ) {
        passkeys.finishRegistration(requireUser(authentication), request);
    }

    @PostMapping("/authentication/options")
    public PasskeyChallengeResponse authenticationOptions(HttpServletRequest request) {
        return passkeys.startAuthentication(request.getRemoteAddr());
    }

    @PostMapping("/authentication/verify")
    public LoginResponse verifyAuthentication(@Valid @RequestBody PasskeyFinishRequest request) {
        return passkeys.finishAuthentication(request);
    }

    private ApiUser requireUser(Authentication authentication) {
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(ApiUser::isEnabled)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid user session is required."
                ));
    }
}
