package org.unipod.unipoderpbackend.service.review;

/**
 * Review quorum for the latest review round of a brief.
 *
 * @param configured whether any review settings exist for the brief
 * @param round      the latest round, or {@code null} when not configured
 * @param required   reviews needed in that round (0 when not configured)
 * @param completed  assignments in that round with status SUBMITTED (0 when not configured)
 * @param met        {@code configured && completed >= required}
 */
public record ReviewQuorum(boolean configured, Integer round, int required, int completed, boolean met) {
    public static ReviewQuorum notConfigured() {
        return new ReviewQuorum(false, null, 0, 0, false);
    }
}
