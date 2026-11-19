package org.unipod.unipoderpbackend.controller.improvement;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request bodies shared by the kept improvement and review tests, moved unchanged from the parked
 * RefinementRequestApiIntegrationTest.body and ImprovementResponseApiIntegrationTest.answerBody
 * (see src/test-parked/PARKED.md). They still build the bodies of the removed endpoints of this module.
 */
final class ImprovementTestBodies {
    private ImprovementTestBodies() {
    }

    static Map<String, Object> body(String guidance, LocalDate dueDate, List<String> changes, Long caseVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guidanceNote", guidance);
        body.put("dueDate", dueDate == null ? null : dueDate.toString());
        body.put("requestedChanges", changes);
        // Main's RefinementRequestAskRequest: the case version goes in "version".
        body.put("version", caseVersion);
        return body;
    }

    static Map<String, Object> answerBody(String whatChanged, Long requestVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        // Main's ReplySubmissionRequest: the what-changed text goes in "note". Main has no request version,
        // so requestVersion is accepted for the callers' signatures but not sent.
        body.put("note", whatChanged);
        return body;
    }
}
