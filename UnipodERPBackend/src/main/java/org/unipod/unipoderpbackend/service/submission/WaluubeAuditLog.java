package org.unipod.unipoderpbackend.service.submission;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WaluubeAuditLog {
    public static final List<String> PATH_PATTERNS = List.of(
            "/submissions",
            "/submissions/**",
            "/auth/change-password",
            "/auth/set-password",
            "/cases",
            "/cases/**",
            "/queues/reception",
            "/me/submissions",
            "/me/capabilities",
            "/me/invoices",
            "/me/invoices/**",
            "/me/bookings",
            "/me/bookings/**",
            "/invoices",
            "/invoices/**",
            "/finance",
            "/finance/**",
            "/facilities",
            "/facilities/**",
            "/bookings",
            "/bookings/**",
            "/dashboard/overview"
    );
    private static final int MAX_REQUEST_PATH_LENGTH = 1024;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public WaluubeAuditLog(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void recordRequest(String actor, String method, String requestPath, String contextPath, int status) {
        String path = UriUtils.decode(requestPath, StandardCharsets.UTF_8);
        String decodedContextPath = contextPath == null || contextPath.isEmpty()
                ? ""
                : UriUtils.decode(contextPath, StandardCharsets.UTF_8);
        if (!decodedContextPath.isEmpty() && path.startsWith(decodedContextPath)) {
            path = path.substring(decodedContextPath.length());
        }
        if (!isWaluubePath(path)) {
            return;
        }
        path = truncatePath(path);
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

    static boolean isWaluubePath(String path) {
        return PATH_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static String truncatePath(String path) {
        int codePointCount = path.codePointCount(0, path.length());
        if (codePointCount <= MAX_REQUEST_PATH_LENGTH) {
            return path;
        }
        return path.substring(0, path.offsetByCodePoints(0, MAX_REQUEST_PATH_LENGTH));
    }
}
