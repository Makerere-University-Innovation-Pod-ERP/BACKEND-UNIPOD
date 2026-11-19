package org.unipod.unipoderpbackend.exception.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MediaConflictException extends ResponseStatusException {
    public MediaConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
