package org.unipod.unipoderpbackend.dto.factfinding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Unknown properties are ignored so a client cannot supply result, completedAt or officerId.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompleteFactFindingRequest(Long version) {
}
