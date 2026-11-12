package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;

public record SubmitterCapabilitiesResponse(
        boolean canCreateBrief,
        boolean canEditDraft,
        boolean canSubmitDraft,
        boolean canRespondToInformationRequest,
        boolean canUploadDocuments,
        boolean canUploadPaymentProof,
        boolean canBook,
        List<Module> modules,
        String message
) {
    public enum Availability {
        AVAILABLE,
        NOT_CONFIGURED
    }

    public record Module(
            String key,
            String label,
            Availability availability,
            String reason
    ) {
    }

    private static final String INVOICES_NOT_CONFIGURED =
            "Invoices and payments are provided by the finance module (D4) and are not configured in this backend.";
    private static final String BOOKINGS_NOT_CONFIGURED =
            "Bookings are provided by the facilities module (F1) and are not configured in this backend.";

    public static SubmitterCapabilitiesResponse forSubmitter() {
        return new SubmitterCapabilitiesResponse(
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                List.of(
                        new Module("BRIEFS", "Briefs", Availability.AVAILABLE, null),
                        new Module("SERVICE_REQUESTS", "Service requests", Availability.AVAILABLE, null),
                        new Module("INVOICES", "Invoices and payments", Availability.NOT_CONFIGURED,
                                INVOICES_NOT_CONFIGURED),
                        new Module("BOOKINGS", "Bookings", Availability.NOT_CONFIGURED,
                                BOOKINGS_NOT_CONFIGURED)
                ),
                "You can create and edit your own drafts, submit them to reception, "
                        + "respond to information requests, and upload documents while a brief is still open."
        );
    }
}
