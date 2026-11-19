package org.unipod.unipoderpbackend.dto.report;

import java.time.LocalDate;
import java.util.List;

public record IncomeTrendResponse(int weeks, long totalUgx, List<Week> series) {
    public record Week(LocalDate weekStart, long incomeUgx) {
    }
}
