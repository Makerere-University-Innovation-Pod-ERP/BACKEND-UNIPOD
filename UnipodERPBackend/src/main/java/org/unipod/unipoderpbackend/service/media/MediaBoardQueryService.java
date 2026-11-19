package org.unipod.unipoderpbackend.service.media;

import java.time.Clock;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.media.MediaBoardOverview;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;
import org.unipod.unipoderpbackend.repository.operations.MediaRequestRepository;

/** Reads the media board. The counts come from the same list the board shows, so they always agree (D-039). */
@Service
public class MediaBoardQueryService {
    private final MediaRequestRepository requests;
    private final Clock clock;

    public MediaBoardQueryService(MediaRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MediaBoardRequest> requests() {
        return requests.findBoard().stream()
                .map(row -> MediaBoardRequest.from(row.getRequest(), row.getStudio(), clock.getZone()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaBoardOverview overview() {
        List<MediaBoardRequest> board = requests();
        return new MediaBoardOverview(
                count(board, MediaBoardStatus.REQUESTED),
                count(board, MediaBoardStatus.SCHEDULED),
                count(board, MediaBoardStatus.IN_PRODUCTION),
                count(board, MediaBoardStatus.DELIVERED),
                board);
    }

    private static long count(List<MediaBoardRequest> board, MediaBoardStatus status) {
        Predicate<MediaBoardRequest> inColumn = request -> request.status() == status;
        return board.stream().filter(inColumn).count();
    }
}
