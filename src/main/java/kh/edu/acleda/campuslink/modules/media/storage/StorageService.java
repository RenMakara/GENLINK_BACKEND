package kh.edu.acleda.campuslink.modules.media.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Provider-agnostic object storage. */
public interface StorageService {

    /** Create a presigned PUT the client uses to upload bytes directly to the store. */
    PresignedUpload presignUpload(String key, String contentType,
                                  String checksumSha256Hex, Duration expiry);

    /** Object metadata from a HEAD — existence, size, type, LastModified. */
    Optional<StoredObject> head(String key);

    /** Verify that the configured object-store bucket is reachable without reading or writing an object. */
    void checkAvailability(Duration timeout);

    /** Short-lived presigned GET for private media. */
    String presignDownload(String key, Duration expiry);

    /** Make PUBLIC media readable for providers that use object ACLs. */
    void makePublic(String key);

    /** Remove a public-read ACL where the provider supports per-object ACLs. */
    void makePrivate(String key);

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
