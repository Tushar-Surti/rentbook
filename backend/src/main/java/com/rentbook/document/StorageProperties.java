package com.rentbook.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible storage: MinIO locally, Cloudflare R2 or AWS S3 in production. {@code publicEndpoint} is
 * the address browsers use when it differs from the server's (MinIO inside Docker Compose, for one).
 */
@ConfigurationProperties("rentbook.storage")
public record StorageProperties(String endpoint, String publicEndpoint, String region, String bucket,
                                String accessKey, String secretKey, boolean pathStyle, boolean createBucket) {

    public boolean configured() {
        return present(bucket) && present(accessKey) && present(secretKey);
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
