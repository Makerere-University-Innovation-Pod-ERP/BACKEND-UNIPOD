package org.unipod.unipoderpbackend.dto.stock;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Adds an item to a department's stock list. The frontend has no screen for this yet; staff need it to fill the
 * list. A missing SKU is generated, and an opening quantity is recorded as a RECEIVED movement.
 */
public record CreateStockItemRequest(
        @NotBlank @Size(max = 255) @NoNullCharacters String name,
        @NotBlank @Size(max = 32) @NoNullCharacters String unit,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal quantity,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minimum,
        @Size(max = 255) @NoNullCharacters String location,
        @Size(max = 64) @NoNullCharacters String sku
) {
}
