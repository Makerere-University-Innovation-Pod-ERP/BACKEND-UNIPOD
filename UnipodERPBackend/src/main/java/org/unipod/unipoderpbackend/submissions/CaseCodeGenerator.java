package org.unipod.unipoderpbackend.submissions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CaseCodeGenerator {
    private static final String NEXT_SEQUENCE_SQL = """
            INSERT INTO code_sequences (prefix, code_year, last_value)
            VALUES (?, ?, 1)
            ON CONFLICT (prefix, code_year)
            DO UPDATE SET last_value = code_sequences.last_value + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbcTemplate;

    public CaseCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(SubmissionCategory category, int year) {
        String prefix = category == SubmissionCategory.STUDENT ? "STU" : "GEN";
        Long sequence = jdbcTemplate.queryForObject(NEXT_SEQUENCE_SQL, Long.class, prefix, year);
        if (sequence == null) {
            throw new IllegalStateException("The case-code sequence could not be allocated.");
        }
        return "%s-%d-%06d".formatted(prefix, year, sequence);
    }
}
