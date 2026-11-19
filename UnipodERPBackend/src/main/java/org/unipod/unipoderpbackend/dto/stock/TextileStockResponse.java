package org.unipod.unipoderpbackend.dto.stock;

import java.util.List;

/** The textile stock list: the items and how many movements were recorded in the last seven days. */
public record TextileStockResponse(List<StockItemResponse> items, long movementsThisWeek) {
}
