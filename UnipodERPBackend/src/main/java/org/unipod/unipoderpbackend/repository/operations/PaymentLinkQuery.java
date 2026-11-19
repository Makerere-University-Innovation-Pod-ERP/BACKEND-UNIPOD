package org.unipod.unipoderpbackend.repository.operations;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;

/**
 * Read-only view of the finance invoices that belong to a booking or a training registration (D-046).
 * It reads the table directly so the department screens never import the finance feature's classes.
 */
@Repository
public class PaymentLinkQuery {
    private static final String SELECT = """
            SELECT i.source_id, i.status,
                   (SELECT p.status FROM invoice_payment_proofs p
                     WHERE p.invoice_id = i.id ORDER BY p.uploaded_at DESC, p.id DESC LIMIT 1) AS latest_proof
              FROM invoices i
            """;

    private final JdbcTemplate jdbcTemplate;

    public PaymentLinkQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Payment status by source id; a source with no invoice is absent. When a source has several invoices the newest wins. */
    public Map<String, PaymentStatus> bySource(String sourceType, Collection<String> sourceIds) {
        Map<String, PaymentStatus> statuses = new HashMap<>();
        if (sourceIds.isEmpty()) {
            return statuses;
        }
        String marks = sourceIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] arguments = new Object[sourceIds.size() + 1];
        arguments[0] = sourceType;
        int index = 1;
        for (String sourceId : sourceIds) {
            arguments[index++] = sourceId;
        }
        jdbcTemplate.query(
                SELECT + "WHERE i.source_type = ? AND i.source_id IN (" + marks + ") ORDER BY i.created_at, i.id",
                resultSet -> {
                    statuses.put(resultSet.getString("source_id"), PaymentStatus.fromInvoice(
                            resultSet.getString("status"), resultSet.getString("latest_proof")));
                },
                arguments);
        return statuses;
    }
}
