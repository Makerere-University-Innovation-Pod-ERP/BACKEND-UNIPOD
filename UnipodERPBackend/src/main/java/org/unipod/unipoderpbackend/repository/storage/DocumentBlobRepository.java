package org.unipod.unipoderpbackend.repository.storage;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.storage.DocumentBlob;

public interface DocumentBlobRepository extends JpaRepository<DocumentBlob, UUID> {
}
