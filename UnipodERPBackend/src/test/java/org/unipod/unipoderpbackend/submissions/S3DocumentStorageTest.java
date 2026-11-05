package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3DocumentStorageTest {
    @Test
    void uploadCreatesOpaqueReferenceAndStoresProvidedBytes() throws Exception {
        S3Client s3 = mock(S3Client.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getSize()).thenReturn(4L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        String reference = new S3DocumentStorage(s3, "test-bucket").upload(file);

        assertTrue(reference.matches("[A-Za-z0-9_-]{1,256}"));
        UUID.fromString(reference);
        var request = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertTrue(request.getValue().key().equals(reference));
        assertTrue(request.getValue().bucket().equals("test-bucket"));
        assertTrue(request.getValue().contentLength() == 4L);
    }

    @Test
    void downloadRejectsUrlLikeReferencesBeforeCallingStorage() {
        S3Client s3 = mock(S3Client.class);
        S3DocumentStorage storage = new S3DocumentStorage(s3, "test-bucket");

        assertThrows(StorageObjectNotFoundException.class, () -> storage.download("https://example.invalid/file"));
    }
}
