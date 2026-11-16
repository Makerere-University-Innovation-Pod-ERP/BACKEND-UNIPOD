package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unipod.unipoderpbackend.dto.operations.MediaDeliverRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaRequestCreateRequest;
import org.unipod.unipoderpbackend.service.operations.MediaRequestService;

@RestController
@RequestMapping("/media/requests")
public class MediaRequestController {
    private final MediaRequestService media;
    public MediaRequestController(MediaRequestService media) { this.media = media; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String, Object> create(@RequestBody @Valid MediaRequestCreateRequest request, Authentication auth) {
        return media.create(request, auth.getName());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID briefId,
            Authentication auth) {
        return media.list(status, briefId, auth.getName(), isMediaStaff(auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable UUID id, Authentication auth) {
        return media.get(id, auth.getName(), isMediaStaff(auth));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String, Object> deliver(@PathVariable UUID id, @RequestBody @Valid MediaDeliverRequest request, Authentication auth) {
        return media.deliver(id, request, auth.getName());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR')")
    public Map<String, Object> complete(@PathVariable UUID id, Authentication auth) {
        return media.complete(id, auth);
    }

    private static boolean isMediaStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMINISTRATOR")
                        || role.equals("ROLE_INNOVATION_COORDINATOR")
                        || role.equals("ROLE_DEPARTMENT_COORDINATOR")
                        || role.equals("ROLE_SECTOR_OFFICER")
                        || role.equals("ROLE_FINANCE_OFFICER"));
    }
}
