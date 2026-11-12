package org.unipod.unipoderpbackend.model.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.partner.InvalidPartnerException;

class PartnerTest {
    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");

    @Test
    void createsPartnerWithAllDetails() {
        UUID id = UUID.randomUUID();

        Partner partner = new Partner(id, "  Acme Ltd ", PartnerType.INDUSTRY_PARTNER,
                LocalDate.of(2027, 3, 1), "Jane Doe", "jane@acme.test", "Prefers email", NOW);

        assertEquals(id, partner.getId());
        assertEquals("Acme Ltd", partner.getName());
        assertEquals(PartnerType.INDUSTRY_PARTNER, partner.getType());
        assertEquals(LocalDate.of(2027, 3, 1), partner.getRenewalDate());
        assertEquals("Jane Doe", partner.getContactPerson());
        assertEquals("jane@acme.test", partner.getContactEmail());
        assertEquals("Prefers email", partner.getNotes());
        assertEquals(NOW, partner.getCreatedAt());
        assertEquals(NOW, partner.getUpdatedAt());
    }

    @Test
    void startsWithoutMouDocumentOrNotification() {
        Partner partner = partner("Acme Ltd");

        assertNull(partner.getDocumentReference());
        assertNull(partner.getLastNotifiedStatus());
    }

    @Test
    void storesBlankOptionalTextAsNull() {
        Partner partner = new Partner(UUID.randomUUID(), "Acme Ltd", PartnerType.FUNDER,
                null, "  ", "", " ", NOW);

        assertNull(partner.getRenewalDate());
        assertNull(partner.getContactPerson());
        assertNull(partner.getContactEmail());
        assertNull(partner.getNotes());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(InvalidPartnerException.class, () -> partner("   "));
        assertThrows(InvalidPartnerException.class, () -> partner(null));
    }

    @Test
    void rejectsNameLongerThanColumn() {
        assertThrows(InvalidPartnerException.class, () -> partner("n".repeat(256)));
    }

    @Test
    void acceptsNameAtColumnLimit() {
        assertEquals(255, partner("n".repeat(255)).getName().length());
    }

    @Test
    void rejectsMissingType() {
        assertThrows(InvalidPartnerException.class,
                () -> new Partner(UUID.randomUUID(), "Acme Ltd", null, null, null, null, null, NOW));
    }

    @Test
    void rejectsOverlongOptionalText() {
        assertThrows(InvalidPartnerException.class, () -> new Partner(UUID.randomUUID(), "Acme Ltd",
                PartnerType.FUNDER, null, null, "e".repeat(255), null, NOW));
        assertThrows(InvalidPartnerException.class, () -> new Partner(UUID.randomUUID(), "Acme Ltd",
                PartnerType.FUNDER, null, null, null, "n".repeat(2001), NOW));
    }

    @Test
    void updateChangesOnlyTheDetailsThatArePresent() {
        Partner partner = new Partner(UUID.randomUUID(), "Acme Ltd", PartnerType.FUNDER,
                LocalDate.of(2027, 3, 1), "Jane Doe", "jane@acme.test", "Old note", NOW);
        Instant later = NOW.plusSeconds(60);

        partner.updateDetails(" New Name ", null, null, null, null, "New note", later);

        assertEquals("New Name", partner.getName());
        assertEquals(PartnerType.FUNDER, partner.getType());
        assertEquals(LocalDate.of(2027, 3, 1), partner.getRenewalDate());
        assertEquals("Jane Doe", partner.getContactPerson());
        assertEquals("jane@acme.test", partner.getContactEmail());
        assertEquals("New note", partner.getNotes());
        assertEquals(NOW, partner.getCreatedAt());
        assertEquals(later, partner.getUpdatedAt());
    }

    @Test
    void updateCanReplaceTypeAndRenewalDate() {
        Partner partner = partner("Acme Ltd");

        partner.updateDetails(null, PartnerType.INDUSTRY_PARTNER, LocalDate.of(2028, 1, 1), null, null, null, NOW);

        assertEquals(PartnerType.INDUSTRY_PARTNER, partner.getType());
        assertEquals(LocalDate.of(2028, 1, 1), partner.getRenewalDate());
    }

    @Test
    void updateWithBlankOptionalTextClearsIt() {
        Partner partner = new Partner(UUID.randomUUID(), "Acme Ltd", PartnerType.FUNDER,
                null, "Jane Doe", "jane@acme.test", "A note", NOW);

        partner.updateDetails(null, null, null, " ", "", " ", NOW);

        assertNull(partner.getContactPerson());
        assertNull(partner.getContactEmail());
        assertNull(partner.getNotes());
    }

    @Test
    void failedUpdateLeavesThePartnerUnchanged() {
        Partner partner = new Partner(UUID.randomUUID(), "Acme Ltd", PartnerType.FUNDER,
                null, "Jane Doe", null, null, NOW);

        assertThrows(InvalidPartnerException.class,
                () -> partner.updateDetails("Renamed", null, null, "p".repeat(256), null, null, NOW.plusSeconds(1)));

        assertEquals("Acme Ltd", partner.getName());
        assertEquals("Jane Doe", partner.getContactPerson());
        assertEquals(NOW, partner.getUpdatedAt());
    }

    @Test
    void updateRejectsBlankName() {
        Partner partner = partner("Acme Ltd");

        assertThrows(InvalidPartnerException.class,
                () -> partner.updateDetails("  ", null, null, null, null, null, NOW));
    }

    @Test
    void renewingMouSetsDateAndDocumentReference() {
        Partner partner = partner("Acme Ltd");
        Instant later = NOW.plusSeconds(60);

        partner.renewMou(LocalDate.of(2028, 3, 1), "  MOU-2028-014 ", later);

        assertEquals(LocalDate.of(2028, 3, 1), partner.getRenewalDate());
        assertEquals("MOU-2028-014", partner.getDocumentReference());
        assertEquals(later, partner.getUpdatedAt());
    }

    @Test
    void renewingMouWithoutReferenceClearsThePreviousOne() {
        Partner partner = partner("Acme Ltd");
        partner.renewMou(LocalDate.of(2028, 3, 1), "MOU-2028-014", NOW);

        partner.renewMou(LocalDate.of(2029, 3, 1), " ", NOW);

        assertEquals(LocalDate.of(2029, 3, 1), partner.getRenewalDate());
        assertNull(partner.getDocumentReference());
    }

    @Test
    void renewingMouRequiresADateAndKeepsTheOldStateOnFailure() {
        Partner partner = partner("Acme Ltd");
        partner.renewMou(LocalDate.of(2028, 3, 1), "MOU-1", NOW);

        assertThrows(InvalidPartnerException.class, () -> partner.renewMou(null, "MOU-2", NOW));
        assertThrows(InvalidPartnerException.class,
                () -> partner.renewMou(LocalDate.of(2030, 1, 1), "r".repeat(501), NOW));

        assertEquals(LocalDate.of(2028, 3, 1), partner.getRenewalDate());
        assertEquals("MOU-1", partner.getDocumentReference());
    }

    private static Partner partner(String name) {
        return new Partner(UUID.randomUUID(), name, PartnerType.ACADEMIC_PARTNER, null, null, null, null, NOW);
    }
}
