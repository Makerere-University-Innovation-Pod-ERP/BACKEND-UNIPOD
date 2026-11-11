package org.unipod.unipoderpbackend.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class S3DocumentStorage implements DocumentStorage {
    private static final Pattern OPAQUE_REFERENCE = Pattern.compile("[A-Za-z0-9_-]{1,256}");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, @Value("${storage.s3.bucket}") String bucket) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("S3_BUCKET must be configured when S3 storage is enabled.");
        }
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        String reference = UUID.randomUUID().toString();
        String contentType = safeContentType(file.getContentType());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(reference)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        try (InputStream input = file.getInputStream()) {
            s3.putObject(request, RequestBody.fromInputStream(input, file.getSize()));
        }
        return reference;
    }

    @Override
    public InputStream download(String reference) {
        if (reference == null || !OPAQUE_REFERENCE.matcher(reference).matches()) {
            throw new StorageObjectNotFoundException();
        }
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(reference).build());
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new StorageObjectNotFoundException();
            }
            throw exception;
        }
    }

    @Override
    public void delete(String reference) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(reference).build());
    }

    private static String safeContentType(String contentType) {
        if (contentType == null || contentType.length() > 255
                || !contentType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }
}
