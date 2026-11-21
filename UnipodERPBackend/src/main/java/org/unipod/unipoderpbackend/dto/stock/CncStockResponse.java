package org.unipod.unipoderpbackend.dto.stock;

import java.util.List;

public record CncStockResponse(List<StockItemResponse> items, long movementsThisWeek) {
}
