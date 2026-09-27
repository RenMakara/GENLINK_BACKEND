package kh.edu.acleda.campuslink.modules.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * S3-backed {@link StorageService}. The only class besides {@link StorageConfig}
 * that touches the AWS SDK — everything else is provider-neutral.
 */
@Slf4j
@Service
@Profile("!test")
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3StorageService(S3Client client, S3Presigner presigner, StorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType,
                                         String checksumSha256Hex, Duration expiry) {
        PutObjectRequest.Builder put = PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .contentType(contentType);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);

        if (checksumSha256Hex != null) {
            // S3 wants the checksum base64-encoded; the DB stores it as hex.
            String base64 = Base64.getEncoder()
                .encodeToString(HexFormat.of().parseHex(checksumSha256Hex));
            put.checksumSHA256(base64);
            headers.put("x-amz-checksum-sha256", base64);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(put.build())
            .build();

        String url = presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUpload(url, headers);
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
            return Optional.of(new StoredObject(
                response.contentLength(), response.contentType(), response.lastModified()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void checkAvailability(Duration timeout) {
        client.headBucket(HeadBucketRequest.builder()
            .bucket(properties.getBucket())
            .overrideConfiguration(configuration -> configuration
                .apiCallTimeout(timeout)
                .apiCallAttemptTimeout(timeout))
            .build());
    }

    @Override
    public String presignDownload(String key, Duration expiry) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build())
            .build();
        return presigner.presignGetObject(request).url().toString();
    }

    @Override
    public void makePublic(String key) {
        if (!properties.isPublicReadAcl()) {
            // AWS S3 buckets with Bucket owner enforced disable ACLs. Public
            // access is provided by the bucket policy/CloudFront OAC instead.
            log.debug("Skipping object ACL for {} because public access is policy-managed", key);
            return;
        }
        client.putObjectAcl(PutObjectAclRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .acl(ObjectCannedACL.PUBLIC_READ)
            .build());
    }

    @Override
    public void makePrivate(String key) {
        if (!properties.isPublicReadAcl()) {
            // Private S3 buckets with CloudFront OAC do not use object ACLs.
            return;
        }
        client.putObjectAcl(PutObjectAclRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .acl(ObjectCannedACL.PRIVATE)
            .build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .build());
    }

    @Override
    public String publicUrl(String key) {
        String base = properties.getCdnBase();
        if (base == null || base.isBlank()) {
            log.warn("app.storage.cdn-base is not set; public URLs will use the raw endpoint");
            return properties.getEndpoint().replaceAll("/+$", "") + "/" + properties.getBucket() + "/" + key;
        }
        return base.replaceAll("/+$", "") + "/" + key;
    }
}
