package org.unipod.unipoderpbackend.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.model.storage.DocumentBlob;
import org.unipod.unipoderpbackend.repository.storage.DocumentBlobRepository;

@Service
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "false", matchIfMissing = true)
public class PostgresDocumentStorage implements DocumentStorage {
    private static final Pattern OPAQUE_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final DocumentBlobRepository blobs;
    private final Clock clock;

    public PostgresDocumentStorage(DocumentBlobRepository blobs, Clock clock) {
        this.blobs = blobs;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String upload(MultipartFile file) throws IOException {
        UUID id = UUID.randomUUID();
        blobs.saveAndFlush(new DocumentBlob(id, file.getBytes(), clock.instant()));
        return id.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream download(String reference) {
        DocumentBlob blob = blobs.findById(parseReference(reference))
                .orElseThrow(StorageObjectNotFoundException::new);
        return new ByteArrayInputStream(blob.getContent());
    }

    @Override
    @Transactional
    public void delete(String reference) {
        UUID id;
        try {
            id = UUID.fromString(reference);
        } catch (RuntimeException exception) {
            return;
        }
        blobs.deleteById(id);
    }

    private static UUID parseReference(String reference) {
        if (reference == null || !OPAQUE_REFERENCE.matcher(reference).matches()) {
            throw new StorageObjectNotFoundException();
        }
        try {
            return UUID.fromString(reference);
        } catch (IllegalArgumentException exception) {
            throw new StorageObjectNotFoundException();
        }
    }
}
