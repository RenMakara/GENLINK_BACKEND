package kh.edu.acleda.campuslink.modules.media.dto;

import java.util.Map;
import java.util.UUID;

/** The presigned upload target. */
public record PresignResponse(
    UUID mediaId,
    String uploadUrl,
    String storageKey,
    Map<String, String> requiredHeaders,
    long expiresInSeconds
) {
}
