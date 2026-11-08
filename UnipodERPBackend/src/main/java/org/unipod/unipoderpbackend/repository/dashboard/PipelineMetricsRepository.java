package org.unipod.unipoderpbackend.repository.dashboard;

import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Repository
public class PipelineMetricsRepository {
    private final JdbcTemplate jdbcTemplate;

    public PipelineMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardOverviewResponse overview(Instant since) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE stage = 'DRAFT') AS draft,
                    count(*) FILTER (WHERE stage = 'RECEIVED') AS received,
                    count(*) FILTER (WHERE stage = 'FORWARDED') AS forwarded,
                    count(*) FILTER (WHERE stage = 'IN_PROGRESS') AS in_progress,
                    count(*) FILTER (WHERE stage = 'COMPLETED') AS completed,
                    count(*) FILTER (WHERE category = 'STUDENT') AS students,
                    count(*) FILTER (WHERE category = 'GENERAL') AS general,
                    count(*) FILTER (WHERE updated_at >= ?) AS recently_updated
                FROM submissions
                """,
                (result, row) -> {
                    Map<SubmissionStage, Long> byStage = new EnumMap<>(SubmissionStage.class);
                    byStage.put(SubmissionStage.DRAFT, result.getLong("draft"));
                    byStage.put(SubmissionStage.RECEIVED, result.getLong("received"));
                    byStage.put(SubmissionStage.FORWARDED, result.getLong("forwarded"));
                    byStage.put(SubmissionStage.IN_PROGRESS, result.getLong("in_progress"));
                    byStage.put(SubmissionStage.COMPLETED, result.getLong("completed"));
                    return new DashboardOverviewResponse(
                            result.getLong("total"),
                            Map.copyOf(byStage),
                            result.getLong("students"),
                            result.getLong("general"),
                            result.getLong("received"),
                            result.getLong("forwarded"),
                            result.getLong("completed"),
                            result.getLong("recently_updated")
                    );
                },
                Timestamp.from(since)
        );
    }

    public QueueCounts receptionQueue(Instant since) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE stage = 'DRAFT') AS in_queue,
                    count(*) FILTER (WHERE stage = 'RECEIVED') AS awaiting_action,
                    count(*) FILTER (WHERE updated_at >= ?) AS recently_updated
                FROM submissions
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
