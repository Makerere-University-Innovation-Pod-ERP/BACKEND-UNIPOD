package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class WaluubeAuditLogTest {
    @Test
    void decodesWaluubePathsAndTruncatesStoredValueToColumnLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        WaluubeAuditLog auditLog = new WaluubeAuditLog(
                jdbcTemplate,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        String encodedLongPath = "/%73ubmissions/" + "x".repeat(1100);

        auditLog.recordRequest("anonymous", "GET", encodedLongPath, "", 401);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), arguments.capture());
        String storedPath = (String) arguments.getValue()[3];
        assertTrue(storedPath.startsWith("/submissions/"));
        assertEquals(1024, storedPath.codePointCount(0, storedPath.length()));
    }
}
