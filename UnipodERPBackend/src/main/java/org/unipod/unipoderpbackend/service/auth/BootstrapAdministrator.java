package org.unipod.unipoderpbackend.service.auth;

import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

@Component
public class BootstrapAdministrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdministrator.class);

    private final AuthenticationService authentication;
    private final Validator validator;
    private final String email;
    private final String password;

    public BootstrapAdministrator(
            AuthenticationService authentication,
            Validator validator,
            @Value("${auth.bootstrap-admin.email:}") String email,
            @Value("${auth.bootstrap-admin.password:}") String password
    ) {
        this.authentication = authentication;
        this.validator = validator;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            log.warn("No bootstrap administrator configured; set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD to create the first administrator.");
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both bootstrap administrator secrets must be configured together.");
        }
        if (!validator.validate(new CreateApiUserRequest(email, password, ApiUserRole.ADMINISTRATOR)).isEmpty()) {
            throw new IllegalStateException("Bootstrap administrator credentials do not meet the account requirements.");
        }
        authentication.bootstrapAdministrator(email, password);
    }
}
