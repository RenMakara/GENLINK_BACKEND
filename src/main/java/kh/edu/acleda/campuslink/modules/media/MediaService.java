package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.dto.ConfirmRequest;
import kh.edu.acleda.campuslink.modules.media.dto.MediaResponse;
import kh.edu.acleda.campuslink.modules.media.dto.PresignRequest;
import kh.edu.acleda.campuslink.modules.media.dto.PresignResponse;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import java.util.List;

import java.util.UUID;

public interface MediaService {

    /** Validate, reserve a key + PENDING row, and return a presigned upload URL. */
    PresignResponse presignUpload(PresignRequest request);

    /** Verify the uploaded object, persist metadata, mark CONFIRMED. */
    MediaResponse confirmUpload(ConfirmRequest request);

    /** A URL to read the media — CDN for PUBLIC, presigned GET for PRIVATE. */
    String downloadUrl(UUID mediaId);

    /** The CDN URL of a confirmed, PUBLIC media object owned by the caller. */
    String resolveConfirmedUrl(UUID mediaId);

    /** Attach an owned, confirmed upload to exactly one post/story resource. */
    void attachMedia(UUID mediaId, MediaResourceType resourceType, UUID resourceId,
                     MediaAttachmentRole role, int order, MediaPurpose expectedPurpose,
                     boolean publicContent);

    /** Link a previously returned owned URL if it came from this media service. */
    boolean attachOwnedUrl(String url, MediaPurpose expectedPurpose, MediaResourceType resourceType,
                           UUID resourceId, MediaAttachmentRole role, int order, boolean publicContent);

    /** Resolve resource media after the owning content service has authorized its viewer. */
    List<AttachedMedia> resolveAttachedMedia(MediaResourceType resourceType, UUID resourceId);

    /** Detach all uploads from an owned resource before replacing its media set. */
    void detachAttachedMedia(MediaResourceType resourceType, UUID resourceId);

    /** Apply a content audience change to every attachment owned by that resource. */
    void updateAttachedMediaVisibility(MediaResourceType resourceType, UUID resourceId, boolean publicContent);

    record AttachedMedia(UUID mediaId, MediaAttachmentRole role, int order, String url) {
        public AttachedMedia(MediaAttachmentRole role, int order, String url) {
            this(null, role, order, url);
        }
    }

    /** Soft delete (owner only). The object is purged later by the cleanup job. */
    void delete(UUID mediaId);
}
