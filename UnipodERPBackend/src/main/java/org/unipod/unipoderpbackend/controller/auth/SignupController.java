package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.dto.auth.SignupRequest;
import org.unipod.unipoderpbackend.service.auth.SignupService;

@RestController
@RequestMapping("/auth")
public class SignupController {
    private final SignupService signup;

    public SignupController(SignupService signup) {
        this.signup = signup;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signUp(@Valid @RequestBody SignupRequest request) {
        return signup.signUp(request);
    }
}
