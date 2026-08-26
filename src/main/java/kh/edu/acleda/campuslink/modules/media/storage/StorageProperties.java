package kh.edu.acleda.campuslink.modules.media.storage;

import java.net.URI;
import java.util.Locale;
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

    /**
     * Fail fast when a standard DigitalOcean CDN hostname does not describe the
     * configured Space and region. Custom CDN domains and other S3-compatible
     * providers remain supported because only DigitalOcean's default hostname is
     * checked.
     */
    public void validateCdnBaseMatchesBucketAndRegion() {
        if (cdnBase == null || cdnBase.isBlank()) {
            return;
        }

        final String host;
        try {
            host = URI.create(cdnBase).getHost();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.storage.cdn-base is not a valid URL", exception);
        }

        if (host == null) {
            throw new IllegalStateException("app.storage.cdn-base must be an absolute URL");
        }

        String digitalOceanCdnSuffix = ".cdn.digitaloceanspaces.com";
        if (host.toLowerCase(Locale.ROOT).endsWith(digitalOceanCdnSuffix)) {
            String expectedHost = bucket + "." + region + digitalOceanCdnSuffix;
            if (!host.equalsIgnoreCase(expectedHost)) {
                throw new IllegalStateException(
                    "DigitalOcean CDN host must match app.storage.bucket and app.storage.region; "
                        + "expected https://" + expectedHost);
            }
        }
    }
}
