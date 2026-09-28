package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.dto.ConfirmRequest;
import kh.edu.acleda.campuslink.modules.media.dto.MediaResponse;
import kh.edu.acleda.campuslink.modules.media.dto.PresignRequest;
import kh.edu.acleda.campuslink.modules.media.dto.PresignResponse;
import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import kh.edu.acleda.campuslink.modules.media.entity.MediaStatus;
import kh.edu.acleda.campuslink.modules.media.entity.MediaVisibility;
import kh.edu.acleda.campuslink.modules.media.repository.MediaFileRepository;
import kh.edu.acleda.campuslink.modules.media.storage.StorageProperties;
import kh.edu.acleda.campuslink.modules.media.storage.StorageService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the presign → confirm → serve → delete lifecycle. The backend
 * never touches the bytes: it signs, validates metadata, and records. Size and
 * type are enforced here on every request, independent of any client-side
 * compression.
 */
@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private final MediaFileRepository repository;
    private final StorageService storage;
    private final StorageProperties properties;
    private final CurrentUserProvider currentUserProvider;

    public MediaServiceImpl(MediaFileRepository repository,
                            StorageService storage,
                            StorageProperties properties,
                            CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public PresignResponse presignUpload(PresignRequest request) {
        UUID ownerId = currentUserProvider.getCurrentUserId();

        MediaPurpose purpose = request.purpose();
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        validateTypeAndSize(purpose, contentType, request.sizeBytes());

        String key = buildKey(purpose, contentType);

        MediaFile media = new MediaFile();
        media.setOwnerId(ownerId);
        media.setStorageKey(key);
        media.setContentType(contentType);
        media.setSizeBytes(request.sizeBytes());
        media.setChecksumSha256(request.checksumSha256());
        media.setPurpose(purpose);
        media.setVisibility(purpose.defaultVisibility());
        media.setStatus(MediaStatus.PENDING);
        media = repository.save(media);

        Duration expiry = Duration.ofSeconds(properties.getPresignExpirySeconds());
        // Pin the checksum only when the provider supports it (off for Spaces).
        String checksumToPin = properties.isEnforceChecksum() ? request.checksumSha256() : null;
        StorageService.PresignedUpload upload =
            storage.presignUpload(key, contentType, checksumToPin, expiry);

        log.debug("Presigned {} upload for user {} key {}", purpose, ownerId, key);
        return new PresignResponse(media.getId(), upload.url(), key,
            upload.requiredHeaders(), expiry.toSeconds());
    }

    @Override
    @Transactional
    public MediaResponse confirmUpload(ConfirmRequest request) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = ownedActiveMedia(request.mediaId(), ownerId);

        if (media.getStatus() == MediaStatus.CONFIRMED) {
            throw new ApiException("Media already confirmed", "MEDIA_ALREADY_CONFIRMED");
        }

        StorageService.StoredObject object = storage.head(media.getStorageKey())
            .orElseThrow(() -> new ApiException(
                "Uploaded object not found in storage", "MEDIA_NOT_UPLOADED"));

        // A 0-byte object means the PUT sent no body (file not attached). Reject
        // it clearly rather than storing a broken record.
        if (object.sizeBytes() <= 0) {
            throw new ApiException("Uploaded object is empty", "MEDIA_EMPTY");
        }

        // Trust the store's actual size over the client's declared size, and
        // re-enforce the cap on it — a client cannot inflate past the limit.
        validateTypeAndSize(media.getPurpose(), media.getContentType(), object.sizeBytes());
        media.setSizeBytes(object.sizeBytes());
        media.setUploadedAt(object.lastModified());

        media.setWidth(request.width());
        media.setHeight(request.height());
        media.setDurationMs(request.durationMs());
        media.setStatus(MediaStatus.CONFIRMED);
        media.setConfirmedAt(Instant.now());
        if (media.getVisibility() == MediaVisibility.PUBLIC) {
            // Legacy providers may use an object ACL. AWS S3 uses its bucket
            // policy/CloudFront OAC instead, so the storage adapter can no-op.
            storage.makePublic(media.getStorageKey());
            media.setPublicUrl(storage.publicUrl(media.getStorageKey()));
        }

        media = repository.save(media);
        log.debug("Confirmed media {} for user {}", media.getId(), ownerId);
        return MediaResponse.from(media);
    }

    @Override
    @Transactional(readOnly = true)
    public String downloadUrl(UUID mediaId) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = repository.findByIdAndDeletedAtIsNull(mediaId)
            .orElseThrow(() -> new NotFoundException("Media not found"));

        if (media.getVisibility() == MediaVisibility.PUBLIC) {
            return media.getPublicUrl() != null
                ? media.getPublicUrl()
                : storage.publicUrl(media.getStorageKey());
        }
        // PRIVATE: only the owner may mint a presigned GET.
        if (!media.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Media not found");
        }
        return storage.presignDownload(media.getStorageKey(),
            Duration.ofSeconds(properties.getPresignExpirySeconds()));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveConfirmedUrl(UUID mediaId) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = ownedActiveMedia(mediaId, ownerId);
        if (media.getStatus() != MediaStatus.CONFIRMED) {
            throw new ApiException("Media has not been confirmed", "MEDIA_NOT_CONFIRMED");
        }
        if (media.getVisibility() != MediaVisibility.PUBLIC || media.getPublicUrl() == null) {
            throw new ApiException("Media is not public", "MEDIA_NOT_PUBLIC");
        }
        return media.getPublicUrl();
    }

    @Override
    @Transactional
    public void attachMedia(UUID mediaId, MediaResourceType resourceType, UUID resourceId,
                            MediaAttachmentRole role, int order, MediaPurpose expectedPurpose,
                            boolean publicContent) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = repository.findByIdAndDeletedAtIsNullForUpdate(mediaId)
            .orElseThrow(() -> new NotFoundException("Media not found"));
        if (!media.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Media not found");
        }
        if (media.getStatus() != MediaStatus.CONFIRMED || media.getPurpose() != expectedPurpose) {
            throw new ApiException("Media is not available for this content", "MEDIA_NOT_ATTACHABLE");
        }

        boolean alreadyAttached = media.getAttachedResourceId() != null;
        if (alreadyAttached && (!resourceId.equals(media.getAttachedResourceId())
            || resourceType != media.getAttachedResourceType()
            || role != media.getAttachmentRole()
            || !Integer.valueOf(order).equals(media.getAttachmentOrder()))) {
            throw new ApiException("Media is already attached to content", "MEDIA_ALREADY_ATTACHED");
        }

        media.setAttachedResourceType(resourceType);
        media.setAttachedResourceId(resourceId);
        media.setAttachmentRole(role);
        media.setAttachmentOrder(order);
        setVisibility(media, publicContent);
        repository.save(media);
    }

    @Override
    @Transactional
    public boolean attachOwnedUrl(String url, MediaPurpose expectedPurpose, MediaResourceType resourceType,
                                  UUID resourceId, MediaAttachmentRole role, int order, boolean publicContent) {
        if (url == null || url.isBlank()) {
            return false;
        }
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = repository.findByOwnerIdAndPublicUrlAndPurposeAndDeletedAtIsNull(
            ownerId, url.strip(), expectedPurpose).orElse(null);
        if (media == null) {
            return false;
        }
        attachMedia(media.getId(), resourceType, resourceId, role, order, expectedPurpose, publicContent);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachedMedia> resolveAttachedMedia(MediaResourceType resourceType, UUID resourceId) {
        return repository.findByAttachedResourceTypeAndAttachedResourceIdAndDeletedAtIsNullOrderByAttachmentRoleAscAttachmentOrderAsc(
                resourceType, resourceId).stream()
            .filter(media -> media.getStatus() == MediaStatus.CONFIRMED)
            .map(media -> new AttachedMedia(
                media.getId(),
                media.getAttachmentRole(),
                media.getAttachmentOrder() == null ? 0 : media.getAttachmentOrder(),
                media.getVisibility() == MediaVisibility.PUBLIC && media.getPublicUrl() != null
                    ? media.getPublicUrl()
                    : storage.presignDownload(media.getStorageKey(),
                        Duration.ofSeconds(properties.getPresignExpirySeconds()))))
            .toList();
    }

    @Override
    @Transactional
    public void detachAttachedMedia(MediaResourceType resourceType, UUID resourceId) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        List<MediaFile> files = repository
            .findByAttachedResourceTypeAndAttachedResourceIdAndDeletedAtIsNullOrderByAttachmentRoleAscAttachmentOrderAsc(
                resourceType, resourceId);
        for (MediaFile media : files) {
            if (!media.getOwnerId().equals(ownerId)) {
                throw new NotFoundException("Content not found");
            }
            media.setAttachedResourceType(null);
            media.setAttachedResourceId(null);
            media.setAttachmentRole(null);
            media.setAttachmentOrder(null);
        }
        repository.saveAll(files);
    }

    @Override
    @Transactional
    public void updateAttachedMediaVisibility(MediaResourceType resourceType, UUID resourceId,
                                             boolean publicContent) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        List<MediaFile> files = repository
            .findByAttachedResourceTypeAndAttachedResourceIdAndDeletedAtIsNullOrderByAttachmentRoleAscAttachmentOrderAsc(
                resourceType, resourceId);
        for (MediaFile media : files) {
            if (!media.getOwnerId().equals(ownerId)) {
                throw new NotFoundException("Content not found");
            }
            setVisibility(media, publicContent);
        }
        repository.saveAll(files);
    }

    @Override
    @Transactional
    public void delete(UUID mediaId) {
        UUID ownerId = currentUserProvider.getCurrentUserId();
        MediaFile media = ownedActiveMedia(mediaId, ownerId);
        media.setDeletedAt(Instant.now());
        repository.save(media);
        log.debug("Soft-deleted media {} for user {}", mediaId, ownerId);
    }

    /** Revoke legacy public ACLs for media that the attachment backfill marked private. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void revokeLegacyPrivateUrls() {
        List<MediaFile> legacyPrivateFiles = repository
            .findByVisibilityAndPublicUrlIsNotNullAndDeletedAtIsNull(MediaVisibility.PRIVATE);
        for (MediaFile media : legacyPrivateFiles) {
            storage.makePrivate(media.getStorageKey());
            media.setPublicUrl(null);
        }
        if (!legacyPrivateFiles.isEmpty()) {
            repository.saveAll(legacyPrivateFiles);
            log.info("Revoked public URLs for {} private media objects", legacyPrivateFiles.size());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Active media owned by the caller, or 404 — never disclosing another's media. */
    private MediaFile ownedActiveMedia(UUID mediaId, UUID ownerId) {
        MediaFile media = repository.findByIdAndDeletedAtIsNull(mediaId)
            .orElseThrow(() -> new NotFoundException("Media not found"));
        if (!media.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Media not found");
        }
        return media;
    }

    private void setVisibility(MediaFile media, boolean publicContent) {
        if (publicContent) {
            if (media.getVisibility() != MediaVisibility.PUBLIC) {
                storage.makePublic(media.getStorageKey());
            }
            media.setVisibility(MediaVisibility.PUBLIC);
            media.setPublicUrl(storage.publicUrl(media.getStorageKey()));
            return;
        }

        if (media.getVisibility() == MediaVisibility.PUBLIC) {
            storage.makePrivate(media.getStorageKey());
        }
        media.setVisibility(MediaVisibility.PRIVATE);
        media.setPublicUrl(null);
    }

    private void validateTypeAndSize(MediaPurpose purpose, String contentType, long sizeBytes) {
        boolean image = contentType.startsWith("image/");
        boolean video = contentType.startsWith("video/");
        if (!image && !video) {
            throw new ApiException("Unsupported media type: " + contentType, "MEDIA_INVALID_TYPE");
        }
        if (video && !purpose.allowsVideo()) {
            throw new ApiException("Video is not allowed for " + purpose, "MEDIA_VIDEO_NOT_ALLOWED");
        }
        long cap = video ? properties.maxVideoBytes() : properties.maxImageBytes();
        if (sizeBytes > cap) {
            throw new ApiException("File exceeds the size limit", "MEDIA_TOO_LARGE");
        }
    }

    private String buildKey(MediaPurpose purpose, String contentType) {
        LocalDate today = LocalDate.now();
        return String.format(Locale.ROOT, "%s/%d/%02d/%s.%s",
            purpose.keyPrefix(), today.getYear(), today.getMonthValue(),
            UUID.randomUUID(), extensionFor(contentType));
    }

    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", "jpg", "image/jpg", "jpg", "image/png", "png",
        "image/webp", "webp", "image/gif", "gif",
        "video/mp4", "mp4", "video/quicktime", "mov", "video/webm", "webm");

    private String extensionFor(String contentType) {
        String ext = EXTENSIONS.get(contentType);
        if (ext != null) {
            return ext;
        }
        int slash = contentType.indexOf('/');
        String subtype = slash >= 0 ? contentType.substring(slash + 1) : "bin";
        // Guard against anything odd in the subtype ending up in an object key.
        return subtype.replaceAll("[^a-z0-9]", "");
    }
}
