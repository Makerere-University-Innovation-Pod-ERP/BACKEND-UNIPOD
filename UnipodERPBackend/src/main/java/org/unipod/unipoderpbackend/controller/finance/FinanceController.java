package org.unipod.unipoderpbackend.controller.finance;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse;
import org.unipod.unipoderpbackend.dto.finance.FinanceSummaryResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.finance.FinanceOverviewService;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@RestController
@RequestMapping("/finance")
public class FinanceController {
    private final InvoiceService invoices;
    private final FinanceOverviewService overview;
    private final CurrentActor currentActor;

    public FinanceController(InvoiceService invoices, FinanceOverviewService overview, CurrentActor currentActor) {
        this.invoices = invoices;
        this.overview = overview;
        this.currentActor = currentActor;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public FinanceSummaryResponse summary() {
        return invoices.summary();
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public FinanceOverviewResponse overview(Authentication authentication) {
        return overview.overview(currentActor.from(authentication));
    }
}
