package org.unipod.unipoderpbackend.repository.improvement;

import java.util.UUID;

/** A brief's revision with the highest version number, as read by {@link CaseRevisionRepository#findLatestForBriefs}. */
public record LatestRevision(UUID briefId, Long revisionId, int versionNumber) {
}
