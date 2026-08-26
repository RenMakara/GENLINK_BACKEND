package kh.edu.acleda.campuslink.modules.media.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StoragePropertiesTest {

    @Test
    void acceptsDigitalOceanCdnMatchingBucketAndRegion() {
        StorageProperties properties = properties(
            "genlink", "sgp1", "https://genlink.sgp1.cdn.digitaloceanspaces.com");

        assertThatCode(properties::validateCdnBaseMatchesBucketAndRegion)
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsDigitalOceanCdnForDifferentBucket() {
        StorageProperties properties = properties(
            "genlink", "sgp1", "https://genlink-media-prod.sgp1.cdn.digitaloceanspaces.com");

        assertThatThrownBy(properties::validateCdnBaseMatchesBucketAndRegion)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expected https://genlink.sgp1.cdn.digitaloceanspaces.com");
    }

    @Test
    void allowsCustomCdnDomain() {
        StorageProperties properties = properties(
            "genlink", "sgp1", "https://media.genlinkkh.com");

        assertThatCode(properties::validateCdnBaseMatchesBucketAndRegion)
            .doesNotThrowAnyException();
    }

    private StorageProperties properties(String bucket, String region, String cdnBase) {
        StorageProperties properties = new StorageProperties();
        properties.setBucket(bucket);
        properties.setRegion(region);
        properties.setCdnBase(cdnBase);
        return properties;
    }
}
