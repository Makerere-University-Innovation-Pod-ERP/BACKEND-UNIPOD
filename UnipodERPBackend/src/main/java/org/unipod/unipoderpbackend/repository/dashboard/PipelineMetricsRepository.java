package org.unipod.unipoderpbackend.repository.dashboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.NeedsAttentionCase;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardPipelineStage;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

@Repository
public class PipelineMetricsRepository {
    private final JdbcTemplate jdbcTemplate;

    public PipelineMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardOverviewResponse overview(Instant monthStart) {
        String query = """
                SELECT count(*) AS total,
                    count(*) FILTER (
                        WHERE s.stage IN ('READY_FOR_REVIEW', 'IN_REVIEW', 'RESUBMITTED')
                    ) AS waiting_for_review,
                    count(*) FILTER (
                        WHERE s.stage = 'IMPROVEMENT_REQUESTED'
                    ) AS needing_improvement,
                    count(*) FILTER (
                        WHERE s.stage = 'COMPLETED' AND EXISTS (
                            SELECT 1 FROM case_stage_history history
                            WHERE history.submission_id = s.id
                              AND history.to_stage = 'COMPLETED'
                              AND history.changed_at >= ?
                        )
                    ) AS completed_this_month,
                    count(*) FILTER (
                        WHERE s.stage IN ('SUBMITTED', 'AWAITING_INFORMATION')
                    ) AS intake,
                    count(*) FILTER (
                        WHERE s.stage IN ('FORWARDED', 'IN_FACT_FINDING')
                    ) AS fact_finding,
                    count(*) FILTER (
                        WHERE s.stage IN ('READY_FOR_REVIEW', 'IN_REVIEW', 'RESUBMITTED')
                    ) AS under_review,
                    count(*) FILTER (
                        WHERE s.stage = 'IMPROVEMENT_REQUESTED'
                    ) AS refinement_requested,
                    count(*) FILTER (
                        WHERE s.stage IN ('CONFIRMED_AS_PROJECT', 'AWAITING_PAYMENT', 'IN_PROGRESS')
                    ) AS further_handling,
                    count(*) FILTER (WHERE s.stage = 'COMPLETED') AS completed
                FROM submissions s
                WHERE s.stage <> 'DRAFT'
                """;

        return jdbcTemplate.queryForObject(query,
                (result, row) -> {
                    Map<DashboardPipelineStage, Long> pipeline = new EnumMap<>(DashboardPipelineStage.class);
                    pipeline.put(DashboardPipelineStage.INTAKE, result.getLong("intake"));
                    pipeline.put(DashboardPipelineStage.FACT_FINDING, result.getLong("fact_finding"));
                    pipeline.put(DashboardPipelineStage.UNDER_REVIEW, result.getLong("under_review"));
                    pipeline.put(DashboardPipelineStage.REFINEMENT_REQUESTED,
                            result.getLong("refinement_requested"));
                    pipeline.put(DashboardPipelineStage.FURTHER_HANDLING, result.getLong("further_handling"));
                    pipeline.put(DashboardPipelineStage.COMPLETED, result.getLong("completed"));
                    return new DashboardOverviewResponse(
                            result.getLong("total"),
                            result.getLong("waiting_for_review"),
                            result.getLong("needing_improvement"),
                            result.getLong("completed_this_month"),
                            Map.copyOf(pipeline),
                            List.of(),
                            null,
                            null
                    );
                },
                Timestamp.from(monthStart)
        );
    }

    public List<NeedsAttentionCase> needsAttention(Instant staleBefore) {
        String query = """
                SELECT s.id, s.code, s.title, s.stage, s.updated_at,
                    CASE s.stage
                        WHEN 'SUBMITTED' THEN 'Awaiting intake review'
                        WHEN 'AWAITING_INFORMATION' THEN 'Awaiting submitter information'
                        WHEN 'FORWARDED' THEN 'Awaiting fact-finding'
                        WHEN 'IN_FACT_FINDING' THEN 'Fact-finding in progress'
                        WHEN 'READY_FOR_REVIEW' THEN 'Awaiting review assignment'
                        WHEN 'IN_REVIEW' THEN 'Review in progress'
                        WHEN 'IMPROVEMENT_REQUESTED' THEN 'Updated information requested'
                        WHEN 'RESUBMITTED' THEN 'Awaiting review'
                        WHEN 'CONFIRMED_AS_PROJECT' THEN 'Awaiting handover'
                        WHEN 'AWAITING_PAYMENT' THEN 'Awaiting payment'
                        WHEN 'IN_PROGRESS' THEN 'Delivery in progress'
                    END AS reason
                FROM submissions s
                WHERE s.stage NOT IN ('DRAFT', 'COMPLETED', 'CLOSED', 'CONFIRMED_AS_PROJECT')
                    AND s.updated_at < ?
                ORDER BY s.updated_at ASC, s.id ASC
                LIMIT 5
                """;
        return jdbcTemplate.query(query, (result, row) -> new NeedsAttentionCase(
                result.getObject("id", java.util.UUID.class),
                result.getString("code"),
                result.getString("title"),
                SubmissionStage.valueOf(result.getString("stage")),
                result.getString("reason"),
                result.getTimestamp("updated_at").toInstant()
        ), Timestamp.from(staleBefore));
    }

    public QueueCounts receptionQueue(Instant since) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE s.stage = 'SUBMITTED') AS in_queue,
                    count(*) FILTER (
                        WHERE s.stage NOT IN ('DRAFT', 'CLOSED', 'COMPLETED')
                          AND (
                              s.stage = 'AWAITING_INFORMATION'
                              OR EXISTS (
                                  SELECT 1 FROM missing_items item
                                  WHERE item.submission_id = s.id AND item.status = 'OPEN'
                              )
                          )
                    ) AS awaiting_action,
                    count(*) FILTER (
                        WHERE s.stage <> 'DRAFT' AND s.updated_at >= ?
                    ) AS recently_updated
                FROM submissions s
                """,
                (result, row) -> new QueueCounts(
                        result.getLong("in_queue"),
                        result.getLong("awaiting_action"),
                        result.getLong("recently_updated")
                ),
                Timestamp.from(since)
        );
    }

    public record QueueCounts(long inQueue, long awaitingAction, long recentlyUpdated) {
    }
}
