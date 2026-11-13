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

    public static SubmitterCapabilitiesResponse forSubmitter() {
        Module invoices = new Module(
                "INVOICES", "Invoices and payments", Availability.AVAILABLE, null
        );
        Module bookings = new Module(
                "BOOKINGS", "Bookings", Availability.AVAILABLE, null
        );
        return new SubmitterCapabilitiesResponse(
                true,
                true,
                true,
                true,
                true,
                invoices.availability() == Availability.AVAILABLE,
                bookings.availability() == Availability.AVAILABLE,
                List.of(
                        new Module("BRIEFS", "Briefs", Availability.AVAILABLE, null),
                        new Module("SERVICE_REQUESTS", "Service requests", Availability.AVAILABLE, null),
                        invoices,
                        bookings
                ),
                "You can create and edit your own drafts, submit them to reception, "
                        + "respond to information requests, upload documents, upload payment proof, "
                        + "view receipts, and request facility bookings."
        );
    }
}
