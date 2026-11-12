package org.unipod.unipoderpbackend.util;

import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

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
    private static final String PROJECT_PREFIX = "PRJ";

    private final JdbcTemplate jdbcTemplate;

    public CaseCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(SubmissionCategory category, int year) {
        return format(prefixFor(category), year, nextSequence(prefixFor(category), year));
    }

    /**
     * Allocates the reference used when an intake brief is confirmed as a
     * project. Project references share the same yearly sequence table as case
     * references but use their own {@code PRJ} prefix.
     */
    public String nextProject(int year) {
        return format(PROJECT_PREFIX, year, nextSequence(PROJECT_PREFIX, year));
    }

    private long nextSequence(String prefix, int year) {
        Long sequence = jdbcTemplate.queryForObject(NEXT_SEQUENCE_SQL, Long.class, prefix, year);
        if (sequence == null) {
            throw new IllegalStateException("The case-code sequence could not be allocated.");
        }
        return sequence;
    }

    private static String prefixFor(SubmissionCategory category) {
        return switch (category) {
            case STUDENT -> "STU";
            case SERVICE_REQUEST -> "SRV";
            case GENERAL -> "GEN";
        };
    }

    private static String format(String prefix, int year, long sequence) {
        return "%s-%d-%06d".formatted(prefix, year, sequence);
    }
}
