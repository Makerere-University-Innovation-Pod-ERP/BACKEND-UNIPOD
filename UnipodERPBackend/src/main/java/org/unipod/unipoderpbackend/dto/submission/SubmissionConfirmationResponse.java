package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * What a submitter sees after a brief has been submitted: the tracking
 * reference, when reception received it, the destination department and the
 * department's WhatsApp group invite link.
 */
public record SubmissionConfirmationResponse(
        UUID id,
        @JsonProperty("tracking_reference") String trackingReference,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("destination_department_id") UUID destinationDepartmentId,
        @JsonProperty("destination_department_name") String destinationDepartmentName,
        @JsonProperty("department_whatsapp_group_invite_link") String departmentWhatsappGroupInviteLink
) {
}
