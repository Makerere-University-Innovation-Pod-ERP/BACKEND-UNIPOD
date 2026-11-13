package org.unipod.unipoderpbackend.controller.finance;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.finance.FinanceSummaryResponse;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@RestController
@RequestMapping("/finance")
public class FinanceController {
    private final InvoiceService invoices;

    public FinanceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public FinanceSummaryResponse summary() {
        return invoices.summary();
    }
}
