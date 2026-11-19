package org.unipod.unipoderpbackend.service.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;

class PhotoMetaTest {
    @Test
    void keepsOnlyTheFileNameWithoutPathOrControlCharacters() {
        assertEquals("needle.jpg", new PhotoMeta("C:\\fakepath\\needle.jpg", "image/jpeg").checkedName());
        assertEquals("a b.png", new PhotoMeta("/tmp/a\u0000 b\n.png", "IMAGE/PNG").checkedName());
    }

    @Test
    void aMissingOrEmptyNameBecomesPhoto() {
        assertEquals("photo", new PhotoMeta(null, "image/png").checkedName());
        assertEquals("photo", new PhotoMeta("  /  ", "image/png").checkedName());
    }

    @Test
    void longNamesAreCutAndOtherTypesRefused() {
        assertEquals(255, new PhotoMeta("x".repeat(400), "image/png").checkedName().length());
        assertThrows(InvalidEquipmentException.class, () -> new PhotoMeta("a.gif", "image/gif").checkedName());
        assertThrows(InvalidEquipmentException.class, () -> new PhotoMeta("a.png", null).checkedName());
    }
}
