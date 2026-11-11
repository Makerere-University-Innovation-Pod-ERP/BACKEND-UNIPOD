package org.unipod.unipoderpbackend.service.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorage {
    String upload(MultipartFile file) throws IOException;

    InputStream download(String reference);

    void delete(String reference);
}
