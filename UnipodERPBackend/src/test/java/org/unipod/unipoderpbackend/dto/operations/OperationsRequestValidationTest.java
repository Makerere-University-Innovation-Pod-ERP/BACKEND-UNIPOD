package org.unipod.unipoderpbackend.dto.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.MediaContractStatus;
import tools.jackson.databind.ObjectMapper;

class OperationsRequestValidationTest {
    @Test
    void mediaRequestAcceptsANonEmailOwnerIdentifier() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var request = new MediaRequestCreateRequest(
                    UUID.randomUUID(), null, "brief-owner-42", "Media support");

            assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Test
    void serviceCatalogueWhatsAppUrlMustUseHttps() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var invalid = new CreateServiceCatalogueItemRequest(
                    DepartmentCode.CNC, "Laser cutting", null, 25000L, "UGX", null,
                    "javascript:alert(1)", true, null, null);
            var valid = new CreateServiceCatalogueItemRequest(
                    DepartmentCode.CNC, "Laser cutting", null, 25000L, "UGX", null,
                    "https://example.org/group", true, null, null);

            assertFalse(validator.validate(invalid).isEmpty());
            assertTrue(validator.validate(valid).isEmpty());
        }
    }

    @Test
    void mediaContractStatusReadsTheExistingSnakeCaseDocumentKey() throws Exception {
        UUID documentId = UUID.randomUUID();
        String json = """
                {"status":"SIGNED","signed_contract_doc_id":"%s"}
                """.formatted(documentId);

        var request = new ObjectMapper().readValue(json, MediaContractStatusRequest.class);

        assertEquals(MediaContractStatus.SIGNED, request.status());
        assertEquals(documentId, request.signedContractDocId());
    }
}
