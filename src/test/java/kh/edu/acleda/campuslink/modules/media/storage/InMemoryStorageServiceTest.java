package kh.edu.acleda.campuslink.modules.media.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryStorageServiceTest {

    private InMemoryStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorageService();
    }

    @Test
    void modelsPresignUploadHeadAndDownloadWithoutExternalStorage() {
        String key = "posts/2026/08/image.jpg";
        String checksum = "a".repeat(64);

        StorageService.PresignedUpload upload = storage.presignUpload(
            key, "image/jpeg", checksum, Duration.ofMinutes(5));
        assertThat(upload.url()).isEqualTo("memory://upload/" + key);
        assertThat(upload.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        assertThat(upload.requiredHeaders()).containsEntry("x-amz-checksum-sha256",
            Base64.getEncoder().encodeToString(HexFormat.of().parseHex(checksum)));
        assertThat(storage.head(key)).isEmpty();

        storage.put(key, 2048, "image/jpeg");

        assertThat(storage.head(key)).get()
            .extracting(StorageService.StoredObject::sizeBytes,
                StorageService.StoredObject::contentType)
            .containsExactly(2048L, "image/jpeg");
        assertThat(storage.presignDownload(key, Duration.ofMinutes(5)))
            .isEqualTo("memory://download/" + key);
    }

    @Test
    void publicAclAndDeleteAreStatefulAndDeleteIsIdempotent() {
        String key = "avatars/user.jpg";
        storage.put(key, 512, "image/jpeg");

        storage.makePublic(key);

        assertThat(storage.isPublic(key)).isTrue();
        assertThat(storage.publicUrl(key)).isEqualTo("memory://public/" + key);

        storage.delete(key);
        storage.delete(key);

        assertThat(storage.head(key)).isEmpty();
        assertThat(storage.isPublic(key)).isFalse();
    }
}
