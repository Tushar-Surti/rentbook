package com.rentbook.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bucket. Browsers upload and download directly, with short-lived presigned URLs; the server only
 * signs those URLs and checks what arrived. It never streams a file itself.
 */
@Service
public class StorageService {

    static final Duration UPLOAD_WINDOW = Duration.ofMinutes(10);
    static final Duration DOWNLOAD_WINDOW = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    StorageService(StorageProperties properties) {
        this.properties = properties;
        if (!properties.configured()) {
            this.client = null;
            this.presigner = null;
            return;
        }
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        Region region = Region.of(StorageProperties.present(properties.region()) ? properties.region() : "auto");
        S3Configuration s3 = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build();

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(s3)
                // R2 and MinIO don't all accept the SDK's newer default checksums; ask only when S3 requires them.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (StorageProperties.present(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        this.client = builder.build();

        S3Presigner.Builder presign = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(s3);
        String browserEndpoint = StorageProperties.present(properties.publicEndpoint())
                ? properties.publicEndpoint() : properties.endpoint();
        if (StorageProperties.present(browserEndpoint)) {
            presign.endpointOverride(URI.create(browserEndpoint));
        }
        this.presigner = presign.build();
    }

    /** A URL the browser can use directly, with the headers it must send along. */
    public record SignedRequest(String url, Map<String, String> headers, Instant expiresAt) {
    }

    public record StoredObject(long size, String contentType) {
    }

    public boolean configured() {
        return client != null;
    }

    /** A PUT the browser makes itself. The content type is signed, so the file must arrive as declared. */
    SignedRequest presignUpload(String key, String contentType) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(UPLOAD_WINDOW)
                .putObjectRequest(put -> put.bucket(properties.bucket()).key(key).contentType(contentType)));
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host")) {
                headers.put(name, String.join(",", values));
            }
        });
        return new SignedRequest(presigned.url().toString(), headers, presigned.expiration());
    }

    SignedRequest presignDownload(String key, String filename, String contentType, boolean inline) {
        String disposition = (inline ? "inline" : "attachment") + "; filename=\""
                + filename.replaceAll("[\"\\\\\\r\\n]", "_") + "\"";
        PresignedGetObjectRequest presigned = presigner.presignGetObject(request -> request
                .signatureDuration(DOWNLOAD_WINDOW)
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)
                        .responseContentType(contentType)
                        .responseContentDisposition(disposition)));
        return new SignedRequest(presigned.url().toString(), Map.of(), presigned.expiration());
    }

    Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head = client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    void delete(String key) {
        client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }

    /** Local development: make the bucket when MinIO doesn't have it yet. */
    @EventListener(ApplicationReadyEvent.class)
    void createBucketIfAsked() {
        if (!configured() || !properties.createBucket()) {
            return;
        }
        try {
            try {
                client.headBucket(request -> request.bucket(properties.bucket()));
            } catch (NoSuchBucketException e) {
                client.createBucket(request -> request.bucket(properties.bucket()));
                log.info("Created storage bucket {}", properties.bucket());
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                client.createBucket(request -> request.bucket(properties.bucket()));
                log.info("Created storage bucket {}", properties.bucket());
            }
        } catch (RuntimeException e) {
            log.warn("Storage at {} isn't reachable; uploads will fail until it is ({})", properties.endpoint(),
                    e.getMessage());
        }
    }
}
