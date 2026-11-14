package org.unipod.unipoderpbackend.repository.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportQueries {
    private final JdbcTemplate jdbc;

    public ReportQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> countByStage() {
        return counts("SELECT stage AS name, count(*) AS total FROM submissions GROUP BY stage");
    }

    public Map<String, Long> countSubmittedByCategory() {
        return counts("SELECT category AS name, count(*) AS total FROM submissions WHERE stage <> 'DRAFT' GROUP BY category");
    }

    private Map<String, Long> counts(String sql) {
        Map<String, Long> totals = new HashMap<>();
        jdbc.query(sql, row -> {
            totals.put(row.getString("name"), row.getLong("total"));
        });
        return totals;
    }

    public List<PaidAmount> paidSince(Instant from) {
        return jdbc.query("""
                SELECT proof.verified_at AS paid_at, invoice.amount AS amount
                FROM invoices invoice
                JOIN invoice_payment_proofs proof ON proof.invoice_id = invoice.id AND proof.status = 'VERIFIED'
                WHERE invoice.status IN ('PAID', 'RECEIPTED') AND proof.verified_at >= ?
                """, (row, index) -> new PaidAmount(
                row.getTimestamp("paid_at").toInstant(), row.getLong("amount")), Timestamp.from(from));
    }

    public List<DepartmentLoad> departmentWorkload() {
        return jdbc.query("""
                SELECT sec.id, sec.name,
                    (SELECT count(*) FROM submissions s
                        WHERE s.responsible_sector_id = sec.id
                          AND s.stage NOT IN ('DRAFT', 'COMPLETED', 'CLOSED')) AS open_cases,
                    (SELECT count(*) FROM invoices i
                        WHERE lower(i.department) = lower(sec.name)
                          AND i.status IN ('WAITING_FOR_PROOF', 'WAITING_FOR_CHECK')) AS open_invoices
                FROM sectors sec
                WHERE sec.active = true
                ORDER BY sec.name
                """, (row, index) -> new DepartmentLoad(
                row.getObject("id", UUID.class), row.getString("name"),
                row.getLong("open_cases"), row.getLong("open_invoices")));
    }

    public record PaidAmount(Instant paidAt, long amountUgx) {
    }

    public record DepartmentLoad(UUID departmentId, String departmentName, long openCases, long openInvoices) {
    }
}
