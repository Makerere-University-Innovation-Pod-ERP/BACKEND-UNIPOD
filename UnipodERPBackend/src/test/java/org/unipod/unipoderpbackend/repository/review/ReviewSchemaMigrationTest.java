package org.unipod.unipoderpbackend.repository.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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
class ReviewSchemaMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void briefIdColumnsAreUuid() {
        assertEquals("uuid", dataType("review_settings", "brief_id"));
        assertEquals("uuid", dataType("review_assignments", "brief_id"));
    }

    @Test
    void identityColumnsAreVarchar254() {
        assertVarchar("review_assignments", "reviewer_id", 254);
        assertVarchar("review_assignments", "assigned_by", 254);
        assertVarchar("review_settings", "set_by", 254);
        assertVarchar("review_follow_up_notes", "created_by", 254);
    }

    @Test
    void textColumnsHaveTheAgreedLengths() {
        assertVarchar("review_assignments", "department", 100);
        assertVarchar("reviews", "comment", 2000);
        assertVarchar("review_follow_up_notes", "note", 1000);
        assertEquals("date", dataType("review_settings", "due_date"));
        assertEquals("timestamp with time zone", dataType("review_settings", "created_at"));
        assertEquals("timestamp with time zone", dataType("review_assignments", "updated_at"));
        assertEquals("timestamp with time zone", dataType("reviews", "submitted_at"));
        assertEquals("timestamp with time zone", dataType("review_follow_up_notes", "created_at"));
    }

    @Test
    void nullabilityAndDefaultsMatchTheDesign() {
        assertEquals("YES", isNullable("review_settings", "due_date"));
        assertEquals("YES", isNullable("review_assignments", "department"));
        assertEquals("NO", isNullable("reviews", "comment"));
        assertEquals("NO", isNullable("reviews", "submitted_at"));
        assertEquals("NO", isNullable("review_settings", "reviews_needed"));
        assertEquals("1", columnDefault("review_settings", "round"));
        assertEquals("1", columnDefault("review_assignments", "round"));
        assertTrue(columnDefault("review_assignments", "status").startsWith("'NOT_STARTED'"));
    }

    @Test
    void reviewsNeededCheckConstraintExists() {
        String clause = jdbcTemplate.queryForObject("""
                SELECT cc.check_clause
                FROM information_schema.check_constraints cc
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_schema = cc.constraint_schema
                 AND tc.constraint_name = cc.constraint_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'review_settings'
                  AND tc.constraint_name = 'ck_review_settings_reviews_needed'
                  AND tc.constraint_type = 'CHECK'
                """, String.class);

        assertTrue(clause.replace(" ", "").contains("reviews_needed>=1"), clause);
    }

    @Test
    void uniqueConstraintsHaveTheAgreedNamesAndColumns() {
        assertEquals(
                List.of("brief_id", "round"),
                constraintColumns("review_settings", "uq_review_settings_brief_round", "UNIQUE"));
        assertEquals(
                List.of("brief_id", "round", "reviewer_id"),
                constraintColumns("review_assignments", "uq_review_assignments_brief_round_reviewer", "UNIQUE"));
        assertEquals(
                List.of("brief_id", "round", "position"),
                constraintColumns("review_assignments", "uq_review_assignments_brief_round_position", "UNIQUE"));
        assertEquals(
                List.of("assignment_id"),
                constraintColumns("reviews", "uq_reviews_assignment", "UNIQUE"));
    }

    @Test
    void reviewerStatusIndexExistsOnTheAgreedColumns() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'review_assignments'
                  AND indexname = 'ix_review_assignments_reviewer_status'
                """, String.class);

        assertTrue(definition.contains("(reviewer_id, status)"), definition);
    }

    @Test
    void foreignKeysExistOnlyBetweenReviewTables() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT con.conname AS name,
                       src.relname AS source_table,
                       dst.relname AS target_table
                FROM pg_constraint con
                JOIN pg_class src ON src.oid = con.conrelid
                JOIN pg_class dst ON dst.oid = con.confrelid
                JOIN pg_namespace ns ON ns.oid = src.relnamespace
                WHERE con.contype = 'f'
                  AND ns.nspname = current_schema()
                  AND src.relname IN ('review_settings', 'review_assignments', 'reviews', 'review_follow_up_notes')
                ORDER BY con.conname
                """);

        assertEquals(
                List.of(
                        Map.of("name", "fk_review_follow_up_notes_review",
                                "source_table", "review_follow_up_notes", "target_table", "reviews"),
                        Map.of("name", "fk_reviews_assignment",
                                "source_table", "reviews", "target_table", "review_assignments")
                ),
                foreignKeys
        );
    }

    private String dataType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String isNullable(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String columnDefault(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private void assertVarchar(String table, String column, int length) {
        assertEquals("character varying", dataType(table, column), table + "." + column);
        Integer actual = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        assertEquals(length, actual, table + "." + column);
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
                  AND tc.table_name = ?
                  AND tc.constraint_name = ?
                  AND tc.constraint_type = ?
                ORDER BY kcu.ordinal_position
                """, String.class, table, constraint, type);
    }
}
