package org.unipod.unipoderpbackend.controller.media;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.media.MediaBoardOverview;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.service.media.MediaBoardQueryService;

/** The media screen's board (frontend {@code services/media.js}). The submitter-facing {@code /media/requests} is separate. */
@RestController
@RequestMapping("/operations/media")
@Validated
public class MediaBoardController {
    // Every role except SUBMITTER may read operations data (D-011); OBSERVER is read-only.
    static final String READ_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER', 'OBSERVER')";

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
