package org.unipod.unipoderpbackend.dto.config;

import java.util.List;
import java.util.Set;
import org.unipod.unipoderpbackend.dto.sector.SectorFilterOption;
import org.unipod.unipoderpbackend.model.config.ReviewMode;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

public record ConfigResponse(
        String caseCodeStudentPrefix,
        String caseCodeGeneralPrefix,
        int caseCodeSequenceDigits,
        ReviewMode defaultReviewMode,
        int defaultRequiredReviews,
        Set<SubmissionCategory> recommendationLetterRequiredFor,
        List<SectorFilterOption> departments
) {
}
