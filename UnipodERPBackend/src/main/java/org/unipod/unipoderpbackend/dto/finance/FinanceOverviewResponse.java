package org.unipod.unipoderpbackend.dto.finance;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record FinanceOverviewResponse(
        String eyebrow,
        String greeting,
        String subtitle,
        List<Metric> metrics,
        List<PipelineStage> pipelineStages,
        List<AttentionItem> needsAttention,
        Income income,
        List<Facility> facilities,
        List<RecentActivity> recentActivities
) {
    public record Metric(
            String id,
            String label,
            Object value,
            String subtext,
            String variant,
            @JsonInclude(JsonInclude.Include.NON_NULL) String icon
    ) {
        public Metric {
            if (variant == null || variant.isBlank()) {
                variant = "default";
            }
        }
    }

    public record PipelineStage(String key, String label, long count) {
    }

    public record AttentionItem(
            String id,
            String index,
            String title,
            String subtext,
            @JsonInclude(JsonInclude.Include.NON_NULL) String category,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sector,
            @JsonInclude(JsonInclude.Include.NON_NULL) String code
    ) {
    }

    public record Income(
            String amountFormatted,
            long amount,
            String currency,
            String subtext,
            List<Bar> bars
    ) {
        public record Bar(String label, long value, String color) {
        }
    }

    public record Facility(String id, String name, long bookings, long capacity, long percentage) {
    }

    public record RecentActivity(
            String id,
            String actor,
            String action,
            String target,
            @JsonInclude(JsonInclude.Include.NON_NULL) String actionSuffix,
            String timestamp
    ) {
    }
}
