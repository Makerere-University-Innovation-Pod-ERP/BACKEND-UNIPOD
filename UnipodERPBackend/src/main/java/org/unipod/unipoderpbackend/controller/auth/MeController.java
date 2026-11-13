package org.unipod.unipoderpbackend.controller.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.auth.MeResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.auth.MeService;

@RestController
public class MeController {
    private final MeService me;
    private final CurrentActor currentActor;

    public MeController(MeService me, CurrentActor currentActor) {
        this.me = me;
        this.currentActor = currentActor;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(Authentication authentication) {
        return me.me(currentActor.from(authentication));
    }
}
