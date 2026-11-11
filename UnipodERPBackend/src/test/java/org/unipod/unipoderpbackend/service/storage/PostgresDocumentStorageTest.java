package org.unipod.unipoderpbackend.service.storage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.model.storage.DocumentBlob;
import org.unipod.unipoderpbackend.repository.storage.DocumentBlobRepository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostgresDocumentStorageTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-03-10T09:30:00Z");

    private final DocumentBlobRepository blobs = mock(DocumentBlobRepository.class);
    private final Clock clock = Clock.fixed(FIXED_TIME, ZoneId.of("Africa/Kampala"));
    private final PostgresDocumentStorage storage = new PostgresDocumentStorage(blobs, clock);

    @Test
    void uploadStoresBytesAndReturnsOpaqueUuidReference() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3, 4});

        String reference = storage.upload(file);

        UUID id = UUID.fromString(reference);
        assertTrue(reference.matches("[A-Za-z0-9_-]{1,256}"));
        ArgumentCaptor<DocumentBlob> captor = ArgumentCaptor.forClass(DocumentBlob.class);
        verify(blobs).saveAndFlush(captor.capture());
        assertEquals(id, captor.getValue().getId());
        assertEquals(FIXED_TIME, captor.getValue().getCreatedAt());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, captor.getValue().getContent());
    }

    @Test
    void downloadReturnsStoredBytes() throws Exception {
        UUID id = UUID.randomUUID();
        when(blobs.findById(id)).thenReturn(Optional.of(new DocumentBlob(id, new byte[]{9, 8, 7}, FIXED_TIME)));

        try (var content = storage.download(id.toString())) {
            assertArrayEquals(new byte[]{9, 8, 7}, content.readAllBytes());
        }
    }

    @Test
    void downloadRejectsUrlLikeReferencesBeforeQueryingStorage() {
        assertThrows(StorageObjectNotFoundException.class, () -> storage.download("https://example.invalid/file"));
        verifyNoInteractions(blobs);
    }

    @Test
    void downloadMissingObjectIsNotFound() {
        UUID id = UUID.randomUUID();
        when(blobs.findById(id)).thenReturn(Optional.empty());

        assertThrows(StorageObjectNotFoundException.class, () -> storage.download(id.toString()));
    }

    @Test
    void deleteRemovesValidReferenceAndIgnoresInvalidOnes() {
        UUID id = UUID.randomUUID();

        storage.delete("https://example.invalid/file");
        verify(blobs, never()).deleteById(id);
        verifyNoInteractions(blobs);

        storage.delete(id.toString());
        verify(blobs).deleteById(id);
    }
}
