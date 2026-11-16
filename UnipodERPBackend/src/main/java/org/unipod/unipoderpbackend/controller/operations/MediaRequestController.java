package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unipod.unipoderpbackend.dto.operations.MediaDeliverRequest;
import org.unipod.unipoderpbackend.service.operations.MediaRequestService;

@RestController
@RequestMapping("/media/requests")
public class MediaRequestController {
    private final MediaRequestService media;
    public MediaRequestController(MediaRequestService media) { this.media = media; }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String, Object> deliver(@PathVariable UUID id, @RequestBody @Valid MediaDeliverRequest request, Authentication auth) {
        return media.deliver(id, request, auth.getName());
    }
}

