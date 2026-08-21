package kh.edu.acleda.campuslink.modules.media.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("test")
public class InMemoryStorageService implements StorageService {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private final Set<String> publicObjects = ConcurrentHashMap.newKeySet();

    @Override
    public PresignedUpload presignUpload(String key, String contentType,
                                         String checksumSha256Hex, Duration expiry) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        if (checksumSha256Hex != null) {
            String checksumBase64 = Base64.getEncoder()
                .encodeToString(HexFormat.of().parseHex(checksumSha256Hex));
            headers.put("x-amz-checksum-sha256", checksumBase64);
        }
        return new PresignedUpload("memory://upload/" + key, Map.copyOf(headers));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public String presignDownload(String key, Duration expiry) {
        return "memory://download/" + key;
    }

    @Override
    public void makePublic(String key) {
        if (objects.containsKey(key)) {
            publicObjects.add(key);
        }
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        publicObjects.remove(key);
    }

    @Override
    public String publicUrl(String key) {
        return "memory://public/" + key;
    }

    /** Simulates the direct PUT that a mobile client performs after presigning. */
    public void put(String key, long sizeBytes, String contentType) {
        objects.put(key, new StoredObject(sizeBytes, contentType, Instant.now()));
    }

    /** Exposes ACL state only for assertions in test code. */
    public boolean isPublic(String key) {
        return publicObjects.contains(key);
    }

    /** Clears singleton state when a test reuses the same Spring context. */
    public void clear() {
        objects.clear();
        publicObjects.clear();
    }
}
