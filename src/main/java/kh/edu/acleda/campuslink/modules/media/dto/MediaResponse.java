package kh.edu.acleda.campuslink.modules.media.dto;

import kh.edu.acleda.campuslink.modules.media.entity.MediaFile;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;

import java.time.Instant;
import java.util.UUID;

/**
 * Media metadata returned to the client. For PUBLIC media {@code url} is the CDN
 * URL; feature modules store the {@code mediaId} and render {@code url}.
 */
public record MediaResponse(
    UUID mediaId,
    String url,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    Integer width,
    Integer height,
    Long durationMs,
    Instant uploadedAt,
    MediaPurpose purpose
) {
    public static MediaResponse from(MediaFile m) {
        return new MediaResponse(
            m.getId(), m.getPublicUrl(), m.getContentType(), m.getSizeBytes(),
            m.getChecksumSha256(), m.getWidth(), m.getHeight(), m.getDurationMs(),
            m.getUploadedAt(), m.getPurpose());
    }
}
