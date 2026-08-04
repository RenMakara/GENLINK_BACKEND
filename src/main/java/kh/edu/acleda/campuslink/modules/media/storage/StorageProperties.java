package kh.edu.acleda.campuslink.modules.media.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage configuration, bound from {@code app.storage.*}.
 *
 * <p>Every value is environment-driven so the same build runs against any
 * S3-compatible provider (DigitalOcean Spaces, AWS S3, Cloudflare R2, MinIO) by
 * changing configuration alone.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** S3 endpoint, e.g. https://sgp1.digitaloceanspaces.com */
    private String endpoint;

    private String region;

    private String bucket;

    /** CDN origin used to build public URLs (no trailing slash). */
    private String cdnBase;

    private String accessKey;

    private String secretKey;

    /** Lifetime of presigned upload/download URLs. */
    private long presignExpirySeconds = 300;

    private long maxImageMb = 5;

    private long maxVideoMb = 50;

    /**
     * When true, the presigned PUT pins {@code x-amz-checksum-sha256} so the store
     * verifies the body on upload. Off by default because DigitalOcean Spaces has
     * partial support and pinning would break uploads there; the checksum is stored
     * either way.
     */
    private boolean enforceChecksum = false;

    /**
     * The one provider quirk we expose. Virtual-hosted style (false) works for
     * DigitalOcean Spaces, AWS S3 and R2; MinIO typically needs path-style (true).
     */
    private boolean pathStyleAccess = false;

    public long maxImageBytes() {
        return maxImageMb * 1024 * 1024;
    }

    public long maxVideoBytes() {
        return maxVideoMb * 1024 * 1024;
    }
}
