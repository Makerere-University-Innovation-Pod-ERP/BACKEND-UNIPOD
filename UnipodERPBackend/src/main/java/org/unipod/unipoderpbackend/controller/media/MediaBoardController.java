package org.unipod.unipoderpbackend.controller.media;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.controller.operations.OperationsRoles;
import org.unipod.unipoderpbackend.dto.media.MediaBoardOverview;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.service.media.MediaBoardQueryService;

/** The media screen's board (frontend {@code services/media.js}). The submitter-facing {@code /media/requests} is separate. */
@RestController
@RequestMapping("/operations/media")
@Validated
public class MediaBoardController {
    static final String READ_ROLES = OperationsRoles.READ;

    private final MediaBoardQueryService board;

    public MediaBoardController(MediaBoardQueryService board) {
        this.board = board;
    }

    @GetMapping("/overview")
    @PreAuthorize(READ_ROLES)
    public MediaBoardOverview overview() {
        return board.overview();
    }

    @GetMapping("/requests")
    @PreAuthorize(READ_ROLES)
    public List<MediaBoardRequest> requests() {
        return board.requests();
    }
}
