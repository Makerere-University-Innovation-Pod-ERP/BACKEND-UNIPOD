package org.unipod.unipoderpbackend.dto.media;

import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;

/** The board's only status call: {@code { status }}. A value outside the four board stages is a malformed body. */
public record UpdateMediaStatusRequest(@NotNull MediaBoardStatus status) {
}
