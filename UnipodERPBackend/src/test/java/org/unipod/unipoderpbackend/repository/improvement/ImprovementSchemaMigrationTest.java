package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ImprovementSchemaMigrationTest {
    private static final List<String> TABLES =
            List.of("refinement_requests", "refinement_requested_changes", "case_revisions");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void briefIdsAreUuidAndPeopleAreVarchar254() {
        assertEquals("uuid", dataType("refinement_requests", "brief_id"));
        assertEquals("uuid", dataType("case_revisions", "brief_id"));
        assertVarchar("refinement_requests", "requested_by", 254);
        assertVarchar("case_revisions", "submitted_by", 254);
    }

    @Test
    void textColumnsDatesAndTimestampsHaveTheAgreedTypes() {
        assertVarchar("refinement_requests", "guidance_note", 2000);
        assertVarchar("refinement_requested_changes", "text", 500);
        assertVarchar("case_revisions", "what_changed", 2000);
        assertEquals("date", dataType("refinement_requests", "due_date"));
        for (String column : List.of("requested_at", "responded_at", "created_at", "updated_at")) {
            assertEquals("timestamp with time zone", dataType("refinement_requests", column), column);
        }
        assertEquals("timestamp with time zone", dataType("case_revisions", "submitted_at"));
        assertEquals("bigint", dataType("refinement_requests", "version"));
        assertEquals("bigint", dataType("case_revisions", "version"));
    }

    @Test
    void nullabilityAndDefaultsMatchTheDesign() {
        assertEquals("YES", isNullable("refinement_requests", "responded_at"));
        for (String column : List.of("brief_id", "cycle", "source", "guidance_note", "due_date", "status",
                "requested_by", "requested_at", "created_at", "updated_at", "version")) {
            assertEquals("NO", isNullable("refinement_requests", column), column);
        }
        for (String column : List.of("request_id", "position", "text")) {
            assertEquals("NO", isNullable("refinement_requested_changes", column), column);
        }
        for (String column : List.of("brief_id", "version_number", "request_id", "what_changed", "submitted_by",
                "submitted_at", "version")) {
            assertEquals("NO", isNullable("case_revisions", column), column);
        }
        assertTrue(columnDefault("refinement_requests", "status").startsWith("'OPEN'"));
    }

    @Test
    void everyConstraintHasTheAgreedName() {
        Set<String> expected = new TreeSet<>(List.of(
                "pk_refinement_requests",
                "pk_refinement_requested_changes",
                "pk_case_revisions",
                "uq_refinement_requests_brief_cycle",
                "uq_refinement_requested_changes_request_position",
                "uq_case_revisions_brief_version",
                "uq_case_revisions_request",
                "fk_refinement_requested_changes_request",
                "fk_case_revisions_request",
                "ck_refinement_requests_cycle",
                "ck_refinement_requests_source",
                "ck_refinement_requests_status",
                "ck_refinement_requested_changes_position",
                "ck_case_revisions_version_number"));
        Set<String> actual = new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace ns ON ns.oid = rel.relnamespace
                WHERE ns.nspname = current_schema()
                  AND rel.relname IN ('refinement_requests', 'refinement_requested_changes', 'case_revisions')
                  AND con.contype IN ('p', 'u', 'f', 'c')
                """, String.class));
        assertEquals(expected, actual);
    }

    @Test
    void uniqueConstraintsCoverTheAgreedColumns() {
        assertEquals(List.of("brief_id", "cycle"),
                constraintColumns("refinement_requests", "uq_refinement_requests_brief_cycle", "UNIQUE"));
        assertEquals(List.of("request_id", "position"), constraintColumns(
                "refinement_requested_changes", "uq_refinement_requested_changes_request_position", "UNIQUE"));
        assertEquals(List.of("brief_id", "version_number"),
                constraintColumns("case_revisions", "uq_case_revisions_brief_version", "UNIQUE"));
        assertEquals(List.of("request_id"),
                constraintColumns("case_revisions", "uq_case_revisions_request", "UNIQUE"));
    }

    @Test
    void checkConstraintsHaveTheAgreedRules() {
        assertTrue(checkClause("ck_refinement_requests_cycle").contains("cycle>=1"));
        assertTrue(checkClause("ck_refinement_requested_changes_position").contains("position>=1"));
        assertTrue(checkClause("ck_case_revisions_version_number").contains("version_number>=2"));
        String source = checkClause("ck_refinement_requests_source");
        assertTrue(source.contains("'FACT_FINDING'") && source.contains("'COORDINATOR'"), source);
        String status = checkClause("ck_refinement_requests_status");
        assertTrue(status.contains("'OPEN'") && status.contains("'RESPONDED'"), status);
    }

    @Test
    void briefStatusIndexExistsOnTheAgreedColumns() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'refinement_requests'
                  AND indexname = 'ix_refinement_requests_brief_status'
                """, String.class);
        assertTrue(definition.contains("(brief_id, status)"), definition);
    }

    @Test
    void foreignKeysExistOnlyBetweenImprovementTables() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT con.conname AS name, src.relname AS source_table, dst.relname AS target_table
                FROM pg_constraint con
                JOIN pg_class src ON src.oid = con.conrelid
                JOIN pg_class dst ON dst.oid = con.confrelid
                JOIN pg_namespace ns ON ns.oid = src.relnamespace
                WHERE con.contype = 'f'
                  AND ns.nspname = current_schema()
                  AND (src.relname IN ('refinement_requests', 'refinement_requested_changes', 'case_revisions')
                       OR dst.relname IN ('refinement_requests', 'refinement_requested_changes', 'case_revisions'))
                ORDER BY con.conname
                """);
        assertEquals(List.of(
                Map.of("name", "fk_case_revisions_request",
                        "source_table", "case_revisions", "target_table", "refinement_requests"),
                Map.of("name", "fk_refinement_requested_changes_request",
                        "source_table", "refinement_requested_changes", "target_table", "refinement_requests")
        ), foreignKeys);
        for (Map<String, Object> key : foreignKeys) {
            assertTrue(TABLES.contains((String) key.get("target_table")), key.toString());
        }
    }

    private String checkClause(String constraint) {
        return jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(con.oid)
                FROM pg_constraint con JOIN pg_namespace ns ON ns.oid = con.connamespace
                WHERE ns.nspname = current_schema() AND con.conname = ?
                """, String.class, constraint).replace(" ", "").replace("\"", "");
    }

    private String dataType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String isNullable(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String columnDefault(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private void assertVarchar(String table, String column, int length) {
        assertEquals("character varying", dataType(table, column), table + "." + column);
        assertEquals(length, jdbcTemplate.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column), table + "." + column);
    }

    private List<String> constraintColumns(String table, String constraint, String type) {
        return jdbcTemplate.queryForList("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                 AND kcu.table_name = tc.table_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ? AND tc.constraint_name = ? AND tc.constraint_type = ?
                ORDER BY kcu.ordinal_position
                """, String.class, table, constraint, type);
    }
}
