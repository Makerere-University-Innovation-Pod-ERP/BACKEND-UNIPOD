package org.unipod.unipoderpbackend.exception.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MediaNotFoundException extends ResponseStatusException {
    public MediaNotFoundException(String reason) {
        super(HttpStatus.NOT_FOUND, reason);
    }

    public static MediaNotFoundException request() {
        return new MediaNotFoundException("Media request not found.");
    }
}
