package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record MediaContractRequest(
        @NotBlank String client,
        @NotBlank String event_name,
        @NotNull LocalDate event_date,
        String venue,
        @Positive long agreed_price,
        String currency,
        JsonNode milestone_payment_dates,
        UUID signed_contract_doc_id,
        JsonNode assigned_staff,
        Boolean generate_invoices
) {}

