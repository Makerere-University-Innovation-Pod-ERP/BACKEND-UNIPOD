package org.unipod.unipoderpbackend.dto.factfinding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Unknown properties are ignored: officer, round, result and completedAt are server-owned,
// while the global Jackson setting rejects unknown fields on every other endpoint.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveFactFindingRequest(
        Boolean titleClear,
        String titleNote,
        Boolean purposeClear,
        String purposeNote,
        Boolean evidenceEnough,
        String evidenceNote,
        Boolean relevantToHub,
        String relevantNote,
        String missingItems,
        Long version
) {
}
