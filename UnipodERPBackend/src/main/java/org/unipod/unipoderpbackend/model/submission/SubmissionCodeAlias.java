package org.unipod.unipoderpbackend.model.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * A previously issued tracking reference for a brief. References are renumbered to the
 * canonical BRF format, and the old value stays searchable through this alias.
 */
@Entity
@Table(name = "submission_code_aliases")
@IdClass(SubmissionCodeAlias.Key.class)
public class SubmissionCodeAlias {
    @Id
    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Id
    @Column(nullable = false, length = 32)
    private String alias;

    protected SubmissionCodeAlias() {
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getAlias() {
        return alias;
    }

    public static class Key implements Serializable {
        private UUID submissionId;
        private String alias;

        public Key() {
        }

        public Key(UUID submissionId, String alias) {
            this.submissionId = submissionId;
            this.alias = alias;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(submissionId, key.submissionId)
                    && Objects.equals(alias, key.alias);
        }

        @Override
        public int hashCode() {
            return Objects.hash(submissionId, alias);
        }
    }
}
