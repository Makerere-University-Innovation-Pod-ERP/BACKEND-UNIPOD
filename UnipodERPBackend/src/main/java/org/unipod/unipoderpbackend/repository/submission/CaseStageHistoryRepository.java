package org.unipod.unipoderpbackend.repository.submission;

import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseStageHistoryRepository extends JpaRepository<CaseStageHistory, UUID> {
    long countBySubmissionId(UUID submissionId);

    List<CaseStageHistory> findAllBySubmissionIdOrderByChangedAtAsc(UUID submissionId);
}
