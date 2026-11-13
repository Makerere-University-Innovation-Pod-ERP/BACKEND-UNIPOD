package org.unipod.unipoderpbackend.service.submission;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.review.ReviewPanelQueryService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class DefaultCaseFileEnrichmentProvider implements CaseFileEnrichmentProvider {
    private static final int ACTIVITY_LIMIT = 20;

    private final ReviewPanelQueryService panels;
    private final AuditEntryRepository auditEntries;
    private final ObjectMapper objectMapper;

    public DefaultCaseFileEnrichmentProvider(
            ReviewPanelQueryService panels,
            AuditEntryRepository auditEntries,
            ObjectMapper objectMapper
    ) {
        this.panels = panels;
        this.auditEntries = auditEntries;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewPanel reviewPanel(UUID caseId) {
        ReviewersPanelResponse panel = panels.panelFor(caseId);
        List<String> reviewerTeam = panel.reviewers().stream()
                .map(ReviewersPanelResponse.Reviewer::reviewerId)
                .filter(Objects::nonNull)
                .toList();
        boolean quorumMet = panel.quorum() != null && panel.quorum().met();
        return new ReviewPanel(Availability.AVAILABLE, reviewerTeam, panel.configured(), quorumMet);
    }

    @Override
    public ActivityFeed activity(UUID caseId) {
        Page<AuditEntry> page = auditEntries.findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
                AuditTrail.SUBMISSION,
                caseId,
                PageRequest.of(0, ACTIVITY_LIMIT)
        );
        return new ActivityFeed(
                Availability.AVAILABLE,
                page.stream()
                        .map(entry -> new ActivityItem(
                                entry.getOccurredAt(),
                                entry.getActor(),
                                entry.getAction(),
                                summary(entry)
                        ))
                        .toList()
        );
    }

    private String summary(AuditEntry entry) {
        JsonNode after = json(entry.getAfterValue());
        return switch (entry.getAction()) {
            case "submission.created" -> "Brief created";
            case "submission.updated" -> "Brief details updated";
            case "submission.registered" -> "Brief registered";
            case "submission.submitted" -> "Brief submitted";
            case "submission.replied" -> "Submitter replied";
            case "submission.forwarded" -> detail("Forwarded to", text(after, "sector"));
            case "submission.information_requested" -> "More information requested";
            case "submission.transitioned" -> detail("Moved to", humanizeStage(text(after, "stage")));
            case "submission.outcome_saved" -> detail("Outcome saved", text(after, "decision"));
            case "submission.confirmed", "submission.confirmed_as_project" ->
                    detail("Confirmed as project", text(after, "projectCode"));
            case "submission.rejected" -> "Brief rejected";
            case "submission.checklist_updated" -> "Reception checklist updated";
            case "submission.missing_items_recorded" -> "Missing items recorded";
            case "submission.missing_item_resolved" -> "Missing item resolved";
            case "submission.reminder_sent" -> "Reminder sent";
            case "submission.fact_finding_saved" -> "Fact-finding notes saved";
            case "submission.fact_finding_completed" -> "Fact-finding marked complete";
            case "submission.review_settings_saved" -> "Reviewers configured";
            case "submission.reviewer_assigned" -> "Reviewer assigned";
            case "submission.reviewer_removed" -> "Reviewer removed";
            case "submission.review_submitted" -> "Review submitted";
            case "submission.review_follow_up_added" -> "Review follow-up added";
            case "submission.document_added" -> "Document added";
            case "submission.document_status_updated" -> "Document status updated";
            default -> humanizeAction(entry.getAction());
        };
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String detail(String label, String value) {
        return value == null ? label : label + " " + value;
    }

    private static String humanizeStage(String stage) {
        return stage == null ? null : humanize(stage);
    }

    private static String humanizeAction(String action) {
        int separator = action == null ? -1 : action.lastIndexOf('.');
        String value = separator < 0 ? action : action.substring(separator + 1);
        return humanize(value == null ? "" : value);
    }

    private static String humanize(String value) {
        String text = value.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return "Brief activity";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
