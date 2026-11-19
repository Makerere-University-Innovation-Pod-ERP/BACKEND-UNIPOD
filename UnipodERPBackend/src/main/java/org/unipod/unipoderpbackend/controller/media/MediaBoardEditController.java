package org.unipod.unipoderpbackend.controller.media;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.dto.media.UpdateMediaDetailsRequest;
import org.unipod.unipoderpbackend.dto.media.UpdateMediaStatusRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.media.MediaBoardService;

/** The media board's writes: moving a card forward and setting its studio, assignee, date and description. */
@RestController
@RequestMapping("/operations/media/requests")
@Validated
public class MediaBoardEditController {
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final MediaBoardService board;
    private final CurrentActor currentActor;

    public MediaBoardEditController(MediaBoardService board, CurrentActor currentActor) {
        this.board = board;
        this.currentActor = currentActor;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(MANAGE_ROLES)
    public MediaBoardRequest changeStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateMediaStatusRequest request, Authentication authentication) {
        return board.changeStatus(id, request.status(), currentActor.from(authentication));
    }

    @PatchMapping("/{id}/details")
    @PreAuthorize(MANAGE_ROLES)
    public MediaBoardRequest updateDetails(
            @PathVariable UUID id, @Valid @RequestBody UpdateMediaDetailsRequest request, Authentication authentication) {
        return board.updateDetails(id, request, currentActor.from(authentication));
    }
}
