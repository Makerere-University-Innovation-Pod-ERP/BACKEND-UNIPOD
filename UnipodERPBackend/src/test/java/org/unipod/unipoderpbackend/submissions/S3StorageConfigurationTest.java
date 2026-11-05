package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3StorageConfigurationTest {
    private final S3StorageConfiguration configuration = new S3StorageConfiguration();

    @Test
    void acceptsHttpAndHttpsEndpoints() {
        try (S3Client http = configuration.s3Client("us-east-1", "http://localhost:9000", true);
             S3Client https = configuration.s3Client("us-east-1", "https://objects.example.invalid", false)) {
            assertDoesNotThrow(() -> http.serviceName());
            assertDoesNotThrow(() -> https.serviceName());
        }
    }

    @Test
    void rejectsUnsafeEndpointsWithoutEchoingCredentials() {
        assertRejected("ftp://objects.example.invalid");
        assertRejected("http://");
        assertRejected("https://user:secret@objects.example.invalid");
        assertRejected("http://user%40example.com:secret@objects.example.invalid");
    }

    private void assertRejected(String endpoint) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configuration.s3Client("us-east-1", endpoint, true)
        );
        assertTrue(exception.getMessage().contains("HTTP(S) URL"));
        assertTrue(!exception.getMessage().contains("secret"));
    }
}
