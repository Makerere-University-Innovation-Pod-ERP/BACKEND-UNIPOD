package org.unipod.unipoderpbackend.repository.submission;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.submission.SubmissionCodeAlias;

public interface SubmissionCodeAliasRepository
        extends JpaRepository<SubmissionCodeAlias, SubmissionCodeAlias.Key> {
    @Query("select alias.submissionId from SubmissionCodeAlias alias where upper(alias.alias) = :alias")
    Optional<UUID> findSubmissionIdByAlias(@Param("alias") String alias);
}
