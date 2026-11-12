package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;

public record MissingItemResponse(
        UUID id,
        MissingItemKind kind,
        String label,
        String note,
        String reason,
        Instant dueAt,
        String message,
        DocumentType documentType,
        MissingItemStatus status,
        String requestedBy,
        Instant requestedAt,
        Instant lastRemindedAt,
        String resolvedBy,
        Instant resolvedAt,
        String resolutionNote
) {
    public static MissingItemResponse from(MissingItem item) {
        return new MissingItemResponse(
                item.getId(),
                item.getKind(),
                item.getLabel(),
                item.getNote(),
                item.getReason(),
                item.getDueAt(),
                item.getMessage(),
                item.getDocumentType(),
                item.getStatus(),
                item.getRequestedBy(),
                item.getRequestedAt(),
                item.getLastRemindedAt(),
                item.getResolvedBy(),
                item.getResolvedAt(),
                item.getResolutionNote()
        );
    }
}
