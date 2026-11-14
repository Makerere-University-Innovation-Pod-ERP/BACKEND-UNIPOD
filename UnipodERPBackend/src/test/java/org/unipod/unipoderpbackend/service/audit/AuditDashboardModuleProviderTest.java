package org.unipod.unipoderpbackend.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;

class AuditDashboardModuleProviderTest {
    private final AuditEntryRepository entries = mock(AuditEntryRepository.class);
    private final AuditDashboardModuleProvider provider = new AuditDashboardModuleProvider(entries);

    @Test
    void activityShowsTheNewestAuditEntriesInPlainWords() {
        AuditEntry entry = mock(AuditEntry.class);
        when(entry.getOccurredAt()).thenReturn(Instant.parse("2026-11-14T08:00:00Z"));
        when(entry.getActor()).thenReturn("finance@example.test");
        when(entry.getAction()).thenReturn("invoice.payment_verified");
        when(entry.getEntityType()).thenReturn("invoice");
        when(entries.search(isNull(), isNull(), isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var feed = provider.recentActivity();

        assertThat(feed.availability()).isEqualTo("AVAILABLE");
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.items().get(0).summary()).isEqualTo("Invoice payment verified (invoice)");
        assertThat(feed.items().get(0).actor()).isEqualTo("finance@example.test");
    }

    @Test
    void equipmentReturnsStayForFidel() {
        assertThat(provider.overdueEquipmentReturns().availability()).isEqualTo("NOT_CONFIGURED");
    }
}
