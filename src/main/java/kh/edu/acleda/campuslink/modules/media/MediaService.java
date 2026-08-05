package kh.edu.acleda.campuslink.modules.media;

import kh.edu.acleda.campuslink.modules.media.dto.ConfirmRequest;
import kh.edu.acleda.campuslink.modules.media.dto.MediaResponse;
import kh.edu.acleda.campuslink.modules.media.dto.PresignRequest;
import kh.edu.acleda.campuslink.modules.media.dto.PresignResponse;

import java.util.UUID;

public interface MediaService {

    /** Validate, reserve a key + PENDING row, and return a presigned upload URL. */
    PresignResponse presignUpload(PresignRequest request);

    /** Verify the uploaded object, persist metadata, mark CONFIRMED. */
    MediaResponse confirmUpload(ConfirmRequest request);

    /** A URL to read the media — CDN for PUBLIC, presigned GET for PRIVATE. */
    String downloadUrl(UUID mediaId);

    /**
     * The CDN URL of a confirmed, PUBLIC media object owned by the caller.
     *
     * <p>The integration primitive for feature modules (avatar, post, event): a
     * feature accepts a {@code mediaId} and calls this to obtain a URL that is
     * provably a real, owned, confirmed upload — rather than trusting an arbitrary
     * client-supplied string. Throws if the media is missing, not owned, not
     * confirmed, or not public.
     */
    String resolveConfirmedUrl(UUID mediaId);

    /** Soft delete (owner only). The object is purged later by the cleanup job. */
    void delete(UUID mediaId);
}
