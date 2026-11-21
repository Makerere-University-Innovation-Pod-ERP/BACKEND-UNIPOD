package org.unipod.unipoderpbackend.dto.telecoms;

import java.util.List;

public record TelecomsRequestListResponse(
        List<TelecomsRequestResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
