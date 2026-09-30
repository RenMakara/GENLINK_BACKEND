package kh.edu.acleda.campuslink.modules.media.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** Wires the S3 client/presigner from {@link StorageProperties}. */
@Configuration
@EnableConfigurationProperties({StorageProperties.class,
    kh.edu.acleda.campuslink.modules.media.MediaCleanupProperties.class})
public class StorageConfig {

    private final StorageProperties properties;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
    }

    private AwsCredentialsProvider credentials() {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();

        if (!hasAccessKey && !hasSecretKey) {
            // On AWS, the EC2 instance role supplies short-lived credentials.
            return DefaultCredentialsProvider.create();
        }
        if (!hasAccessKey || !hasSecretKey) {
            throw new IllegalStateException(
                "STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY must be set together");
        }
        // Retain static credentials for S3-compatible providers that do not
        // support an AWS instance role, such as a legacy Spaces deployment.
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private S3Configuration serviceConfig() {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(properties.isPathStyleAccess())
            .build();
    }

    @Bean
    @Profile("!test")
    public S3Client s3Client() {
        properties.validateCdnBaseMatchesBucketAndRegion();
        return S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfig())
            .build();
    }

    @Bean
    @Profile("!test")
    public S3Presigner s3Presigner() {
        properties.validateCdnBaseMatchesBucketAndRegion();
        return S3Presigner.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfig())
            .build();
    }
}
