package kh.edu.acleda.campuslink.modules.media.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Wires the S3 client/presigner from {@link StorageProperties}. This and
 * {@link S3StorageService} are the only classes that reference the AWS SDK, so
 * the provider is swappable without touching anything else.
 */
@Configuration
@EnableConfigurationProperties({StorageProperties.class,
    kh.edu.acleda.campuslink.modules.media.MediaCleanupProperties.class})
public class StorageConfig {

    private final StorageProperties properties;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    private S3Configuration serviceConfig() {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(properties.isPathStyleAccess())
            .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfig())
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials())
            .serviceConfiguration(serviceConfig())
            .build();
    }
}
