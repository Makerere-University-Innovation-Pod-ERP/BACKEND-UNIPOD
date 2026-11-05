package org.unipod.unipoderpbackend.submissions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class S3StorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client s3Client(
            @Value("${storage.s3.region}") String region,
            @Value("${storage.s3.endpoint:}") String endpoint,
            @Value("${storage.s3.path-style-access:true}") boolean pathStyleAccess
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());
        if (!endpoint.isBlank()) {
            URI endpointUri;
            try {
                endpointUri = URI.create(endpoint);
            } catch (IllegalArgumentException exception) {
                throw invalidEndpoint();
            }
            if (!("https".equalsIgnoreCase(endpointUri.getScheme())
                    || "http".equalsIgnoreCase(endpointUri.getScheme()))
                    || endpointUri.getHost() == null
                    || endpointUri.getUserInfo() != null) {
                throw invalidEndpoint();
            }
            builder.endpointOverride(endpointUri);
        }
        return builder.build();
    }

    private static IllegalStateException invalidEndpoint() {
        return new IllegalStateException("S3 endpoint must be an HTTP(S) URL without embedded credentials.");
    }
}
