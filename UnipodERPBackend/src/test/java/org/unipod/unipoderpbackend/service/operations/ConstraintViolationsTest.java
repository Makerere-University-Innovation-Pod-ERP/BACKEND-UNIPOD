package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.equipment.EquipmentItemRepository;

/** Uses a real unique violation, so the check is proved against what the database and Hibernate actually report. */
@SpringBootTest
@Transactional
class ConstraintViolationsTest {
    @Autowired
    private EquipmentItemRepository equipment;

    @Test
    void aRealDuplicateAssetTagIsRecognisedByItsConstraintName() {
        String tag = "CV-" + UUID.randomUUID();
        equipment.saveAndFlush(item(tag));

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> equipment.saveAndFlush(item(tag.toLowerCase(java.util.Locale.ROOT))));

        assertTrue(ConstraintViolations.named(failure, "uq_equipment_items_asset_tag"));
        assertFalse(ConstraintViolations.named(failure, "uq_machines_department_name"));
    }

    @Test
    void textThatMerelyMentionsAConstraintIsNotAViolationOfIt() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "value too long, uq_equipment_items_asset_tag");

        assertFalse(ConstraintViolations.named(failure, "uq_equipment_items_asset_tag"));
    }

    private static EquipmentItem item(String tag) {
        return new EquipmentItem(UUID.randomUUID(), DepartmentCode.TELECOMS, "Constraint item", "", tag, 1, "", "",
                Instant.now());
    }
}
