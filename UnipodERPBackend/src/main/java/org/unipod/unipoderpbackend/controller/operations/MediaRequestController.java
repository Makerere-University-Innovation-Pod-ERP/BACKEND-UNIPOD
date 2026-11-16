package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> create(@RequestBody @Valid MediaRequestCreateRequest request, Authentication auth) {
        return media.create(request, auth.getName());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID briefId) {
        return media.list(status, briefId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable UUID id) { return media.get(id); }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String, Object> deliver(@PathVariable UUID id, @RequestBody @Valid MediaDeliverRequest request, Authentication auth) {
        return media.deliver(id, request, auth.getName());
    }
}
