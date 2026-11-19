package org.unipod.unipoderpbackend.dto.stock;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

/** Records telecoms stock: `{itemId, type, quantity, note?, requestId?}`. */
public record RecordTelecomsMovementRequest(
        @NotNull UUID itemId,
        @NotNull StockMovementType type,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal quantity,
        @Size(max = 1000) @NoNullCharacters String note,
        @Size(max = 64) @NoNullCharacters String requestId
) {
}
