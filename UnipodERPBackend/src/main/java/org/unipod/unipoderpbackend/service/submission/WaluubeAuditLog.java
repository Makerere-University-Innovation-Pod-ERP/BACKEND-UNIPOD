package org.unipod.unipoderpbackend.service.submission;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WaluubeAuditLog {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public WaluubeAuditLog(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void recordRequest(String actor, String method, String requestPath, String contextPath, int status) {
        String path = requestPath;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!isWaluubePath(path)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO audit_events (
                    id, actor, http_method, request_path, response_status, occurred_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                actor,
                method,
                path,
                status,
                Timestamp.from(clock.instant())
        );
    }

    private static boolean isWaluubePath(String path) {
        return path.equals("/submissions")
                || path.startsWith("/submissions/")
                || path.equals("/queues/reception")
                || path.equals("/me/submissions")
                || path.equals("/me/capabilities")
                || path.equals("/cases")
                || path.startsWith("/cases/")
                || path.equals("/dashboard/overview");
    }
}
