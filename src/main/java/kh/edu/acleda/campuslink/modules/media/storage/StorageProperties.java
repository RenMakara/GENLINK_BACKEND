package kh.edu.acleda.campuslink.modules.media.storage;

import java.net.URI;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Object-storage configuration, bound from {@code app.storage.*}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** S3 endpoint, e.g. https://s3.ap-southeast-1.amazonaws.com */
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

    /** When true, the presigned PUT pins {@code x-amz-checksum-sha256} so the store verifies the body on upload. */
    private boolean enforceChecksum = false;

    /** The one provider quirk we expose. */
    private boolean pathStyleAccess = false;

    /** Whether PUBLIC media should receive an object-level public-read ACL. */
    private boolean publicReadAcl = true;

    public long maxImageBytes() {
        return maxImageMb * 1024 * 1024;
    }

    public long maxVideoBytes() {
        return maxVideoMb * 1024 * 1024;
    }

    /** Fail fast when a recognized provider CDN hostname does not describe the configured bucket and region. */
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
