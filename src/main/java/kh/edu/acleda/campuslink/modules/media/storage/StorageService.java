package kh.edu.acleda.campuslink.modules.media.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-agnostic object storage. The only abstraction the rest of the app
 * depends on — nothing outside {@code storage} knows which provider is live.
 *
 * <p>The implementation speaks S3, so AWS S3 and other S3-compatible providers
 * are reachable through the same interface with only configuration
 * changes.
 */
public interface StorageService {

    /**
     * Create a presigned PUT the client uses to upload bytes directly to the store.
     *
     * @param key                object key within the bucket
     * @param contentType        pinned so the upload must match what was declared
     * @param checksumSha256Hex  lowercase-hex SHA-256, or null; pinned only when
     *                           checksum enforcement is enabled for the provider
     * @param expiry             how long the URL stays valid
     * @return the URL plus the exact headers the client must send on the PUT
     */
    PresignedUpload presignUpload(String key, String contentType,
                                  String checksumSha256Hex, Duration expiry);

    /** Object metadata from a HEAD — existence, size, type, LastModified. */
    Optional<StoredObject> head(String key);

    /**
     * Verify that the configured object-store bucket is reachable without reading or writing an
     * object. Implementations must honor the supplied bound and must not retry in a loop.
     */
    void checkAvailability(Duration timeout);

    /** Short-lived presigned GET for private media. */
    String presignDownload(String key, Duration expiry);

    /**
     * Make PUBLIC media readable for providers that use object ACLs. This is a
     * no-op for AWS S3 with a private bucket and CloudFront OAC, where a policy
     * grants CloudFront access instead.
     */
    void makePublic(String key);

    /** Remove the object. Idempotent — a missing object is not an error. */
    void delete(String key);

    /** Public CDN URL for an object key. */
    String publicUrl(String key);

    /** A presigned upload target: the URL and the headers that must accompany it. */
    record PresignedUpload(String url, Map<String, String> requiredHeaders) {
    }

    /** What a HEAD tells us — metadata only, never content. */
    record StoredObject(long sizeBytes, String contentType, Instant lastModified) {
    }
}
