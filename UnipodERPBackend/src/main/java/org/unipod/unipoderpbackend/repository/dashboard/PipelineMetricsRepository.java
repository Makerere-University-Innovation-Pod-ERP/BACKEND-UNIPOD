package org.unipod.unipoderpbackend.repository.dashboard;

import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.NeedsAttentionCase;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardPipelineStage;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class PipelineMetricsRepository {
    /**
     * Row visibility shared by every dashboard aggregate. Drafts are private to
     * their owner; the remaining scopes see a brief once it leaves
     * {@code DRAFT}.
     */
    private static final String VISIBLE_CASE = """
            (
                (s.stage <> 'DRAFT' OR s.registered_by = ? OR s.submitter_id = ?)
                AND (
                    ? = 'ALL'
                    OR (? = 'SUBMITTER' AND s.submitter_id = ?)
                    OR (? IN ('REVIEWER', 'REVIEWER_OR_SECTOR_OFFICER')
                        AND s.assigned_reviewer_subject = ?)
                    OR (? IN ('SECTOR_OFFICER', 'REVIEWER_OR_SECTOR_OFFICER') AND EXISTS (
                        SELECT 1 FROM sector_members sm
                        WHERE sm.sector_id = s.responsible_sector_id AND sm.subject = ?
                    ))
                )
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public PipelineMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardOverviewResponse overview(Instant since) {
        return overview(since, "ALL", "");
    }

    public DashboardOverviewResponse overview(Instant since, String scope, String principal) {
        String query = """
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE s.stage = 'DRAFT') AS draft,
                    count(*) FILTER (WHERE s.stage = 'SUBMITTED') AS submitted,
                    count(*) FILTER (WHERE s.stage = 'AWAITING_INFORMATION') AS awaiting_information,
                    count(*) FILTER (WHERE s.stage = 'RECEIVED') AS received,
                    count(*) FILTER (WHERE s.stage = 'REGISTERED') AS registered,
                    count(*) FILTER (WHERE s.stage = 'FACT_FINDING') AS fact_finding,
                    count(*) FILTER (WHERE s.stage = 'FORWARDED') AS forwarded,
                    count(*) FILTER (WHERE s.stage = 'UNDER_REVIEW') AS under_review,
                    count(*) FILTER (WHERE s.stage = 'IN_PROGRESS') AS in_progress,
                    count(*) FILTER (WHERE s.stage = 'REFINEMENT_REQUESTED') AS refinement_requested,
                    count(*) FILTER (WHERE s.stage = 'FURTHER_HANDLING') AS further_handling,
                    count(*) FILTER (WHERE s.stage = 'CONFIRMED_AS_PROJECT') AS confirmed_as_project,
                    count(*) FILTER (WHERE s.stage = 'REJECTED') AS rejected,
                    count(*) FILTER (WHERE s.stage = 'COMPLETED') AS completed,
                    count(*) FILTER (WHERE s.category = 'STUDENT') AS students,
                    count(*) FILTER (WHERE s.category = 'GENERAL') AS general,
                    count(*) FILTER (WHERE s.updated_at >= ?) AS recently_updated,
                    count(*) FILTER (WHERE s.stage IN ('FACT_FINDING', 'FORWARDED')) AS awaiting_review,
                    count(*) FILTER (
                        WHERE s.stage IN ('SUBMITTED', 'AWAITING_INFORMATION', 'RECEIVED')
                    ) AS awaiting_action,
                    count(*) FILTER (
                        WHERE s.stage IN ('SUBMITTED', 'AWAITING_INFORMATION', 'RECEIVED', 'REGISTERED')
                    ) AS intake,
                    count(*) FILTER (WHERE s.stage IN ('UNDER_REVIEW', 'IN_PROGRESS')) AS review
                FROM submissions s
                WHERE """ + VISIBLE_CASE;

        List<Object> arguments = new ArrayList<>();
        arguments.add(Timestamp.from(since));
        addVisibilityArguments(arguments, scope, principal);

        return jdbcTemplate.queryForObject(query,
                (result, row) -> {
                    Map<SubmissionStage, Long> byStage = new EnumMap<>(SubmissionStage.class);
                    for (SubmissionStage stage : SubmissionStage.values()) {
                        byStage.put(stage, result.getLong(stage.name().toLowerCase(Locale.ROOT)));
                    }
                    Map<DashboardPipelineStage, Long> pipeline = new EnumMap<>(DashboardPipelineStage.class);
                    pipeline.put(DashboardPipelineStage.INTAKE, result.getLong("intake"));
                    pipeline.put(DashboardPipelineStage.FACT_FINDING, result.getLong("awaiting_review"));
                    pipeline.put(DashboardPipelineStage.UNDER_REVIEW, result.getLong("review"));
                    pipeline.put(DashboardPipelineStage.REFINEMENT_REQUESTED,
                            result.getLong("refinement_requested"));
                    pipeline.put(DashboardPipelineStage.FURTHER_HANDLING, result.getLong("further_handling"));
                    pipeline.put(DashboardPipelineStage.COMPLETED, result.getLong("completed"));
                    return new DashboardOverviewResponse(
                            result.getLong("total"),
                            Map.copyOf(byStage),
                            result.getLong("students"),
                            result.getLong("general"),
                            result.getLong("awaiting_action"),
                            result.getLong("forwarded"),
                            result.getLong("completed"),
                            result.getLong("recently_updated"),
                            result.getLong("awaiting_review"),
                            result.getLong("refinement_requested"),
                            Map.copyOf(pipeline),
                            List.of(),
                            null,
                            null,
                            null
                    );
                },
                arguments.toArray()
        );
    }

    public List<NeedsAttentionCase> needsAttention(String scope, String principal) {
        String query = """
                SELECT s.id, s.code, s.title, s.stage, s.updated_at,
                    CASE s.stage
                        WHEN 'DRAFT' THEN 'Awaiting submission'
                        WHEN 'SUBMITTED' THEN 'Awaiting intake review'
                        WHEN 'AWAITING_INFORMATION' THEN 'Awaiting submitter information'
                        WHEN 'RECEIVED' THEN 'Awaiting intake review'
                        WHEN 'REGISTERED' THEN 'Awaiting assessment'
                        WHEN 'FACT_FINDING' THEN 'Assessment in progress'
                        WHEN 'FORWARDED' THEN 'Assessment in progress'
                        WHEN 'UNDER_REVIEW' THEN 'Review in progress'
                        WHEN 'IN_PROGRESS' THEN 'Review in progress'
                        WHEN 'REFINEMENT_REQUESTED' THEN 'Updated information requested'
                        WHEN 'FURTHER_HANDLING' THEN 'Awaiting sector response'
                        WHEN 'CONFIRMED_AS_PROJECT' THEN 'Confirmed as project'
                    END AS reason
                FROM submissions s
                WHERE s.stage NOT IN ('COMPLETED', 'REJECTED')
                    AND """ + VISIBLE_CASE + """
                ORDER BY s.updated_at ASC, s.id ASC
                LIMIT 5
                """;
        List<Object> arguments = new ArrayList<>();
        addVisibilityArguments(arguments, scope, principal);
        return jdbcTemplate.query(query, (result, row) -> new NeedsAttentionCase(
                result.getObject("id", java.util.UUID.class),
                result.getString("code"),
                result.getString("title"),
                SubmissionStage.valueOf(result.getString("stage")),
                result.getString("reason"),
                result.getTimestamp("updated_at").toInstant()
        ), arguments.toArray());
    }

    public QueueCounts receptionQueue(Instant since) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE s.stage = 'SUBMITTED') AS in_queue,
                    count(*) FILTER (
                        WHERE s.stage NOT IN ('DRAFT', 'REJECTED', 'COMPLETED')
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

    private static void addVisibilityArguments(
            List<Object> arguments,
            String scope,
            String principal
    ) {
        arguments.add(principal);
        arguments.add(principal);
        arguments.add(scope);
        arguments.add(scope);
        arguments.add(principal);
        arguments.add(scope);
        arguments.add(principal);
        arguments.add(scope);
        arguments.add(principal);
    }

    public record QueueCounts(long inQueue, long awaitingAction, long recentlyUpdated) {
    }
}
