package org.unipod.unipoderpbackend.dto.report;

import java.util.Map;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record StageCountsResponse(long total, long drafts, Map<SubmissionStage, Long> byStage) {
}
