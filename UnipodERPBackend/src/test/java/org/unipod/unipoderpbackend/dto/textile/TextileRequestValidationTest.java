package org.unipod.unipoderpbackend.dto.textile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextileRequestValidationTest {
    @Test
    void persistedTextFieldsRejectNullCharacters() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertFalse(validator.validate(new TextileOrderRequest(
                    "Customer\u0000", "Public", "Curtains", "2 panels", "Blue cotton",
                    null, true, false)).isEmpty());
            assertFalse(validator.validate(new TextileMessageRequest("Details\u0000 requested")).isEmpty());
            assertFalse(validator.validate(new TextileReasonRequest("Needs correction\u0000")).isEmpty());
            assertFalse(validator.validate(new TextileCollectionRequest("Customer\u0000")).isEmpty());
        }
    }

    @Test
    void quoteRejectsNullMaterialsAndFractionalPriceQuantities() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var nullMaterial = new TextileQuoteRequest(Arrays.asList((TextileMaterialLine) null),
                    UUID.randomUUID(), BigDecimal.ONE, java.time.LocalDate.now());
            var fractionalPrice = new TextileQuoteRequest(List.of(
                    new TextileMaterialLine(UUID.randomUUID(), BigDecimal.ONE)),
                    UUID.randomUUID(), new BigDecimal("1.5"), java.time.LocalDate.now());
            var outOfRangePrice = new TextileQuoteRequest(List.of(
                    new TextileMaterialLine(UUID.randomUUID(), BigDecimal.ONE)),
                    UUID.randomUUID(), new BigDecimal("2147483648"), java.time.LocalDate.now());

            assertFalse(validator.validate(nullMaterial).isEmpty());
            assertFalse(validator.validate(fractionalPrice).isEmpty());
            assertFalse(validator.validate(outOfRangePrice).isEmpty());
        }
    }

    @Test
    void qualityRejectsNullItemsAndIds() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var invalid = new TextileQualityRequest(Arrays.asList(
                    null, new TextileQualityRequest.QualityItem(null, false, "Dimensions")));
            var blankId = new TextileQualityRequest(List.of(
                    new TextileQualityRequest.QualityItem(" ", false, "Dimensions")));
            var emptyChecklist = new TextileQualityRequest(List.of());

            assertFalse(validator.validate(invalid).isEmpty());
            assertFalse(validator.validate(blankId).isEmpty());
            assertFalse(validator.validate(emptyChecklist).isEmpty());
        }
    }

    @Test
    void productionTextCannotContainNullCharacters() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var invalid = new TextileProductionRequest("Cotton", null, "Maker", "bad\u0000note");
            var valid = new TextileProductionRequest("Cotton", null, "Maker", "Clean notes");

            assertFalse(validator.validate(invalid).isEmpty());
            assertTrue(validator.validate(valid).isEmpty());
        }
    }
}
