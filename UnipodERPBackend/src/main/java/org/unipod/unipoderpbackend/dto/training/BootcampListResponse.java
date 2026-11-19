package org.unipod.unipoderpbackend.dto.training;

import java.util.List;

/** The frontend reads the list as {@code { items }}. */
public record BootcampListResponse(List<BootcampResponse> items) {
}
