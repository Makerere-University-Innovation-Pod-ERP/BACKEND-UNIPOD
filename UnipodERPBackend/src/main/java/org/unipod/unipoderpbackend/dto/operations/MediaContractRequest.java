package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record MediaContractRequest(
        @NotBlank @Size(max = 255) String client,
        @NotBlank @Size(max = 255) String event_name,
        @NotNull LocalDate event_date,
        @Size(max = 255) String venue,
        @Positive long agreed_price,
        @Pattern(regexp = "(?i)^[A-Z]{3}$") @Size(max = 3) String currency,
        @Email @Size(max = 254) String customer_email,
        JsonNode milestone_payment_dates,
        UUID signed_contract_doc_id,
        JsonNode assigned_staff,
        Boolean generate_invoices
) {}

