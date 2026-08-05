package kh.edu.acleda.campuslink.modules.media.dto;

import java.util.Map;
import java.util.UUID;

/**
 * The presigned upload target.
 *
 * @param mediaId          the PENDING record; echoed back at confirm
 * @param uploadUrl        presigned PUT the client uploads bytes to, directly
 * @param storageKey       object key that was reserved
 * @param requiredHeaders  headers the client MUST send verbatim on the PUT
 * @param expiresInSeconds validity window of the URL
 */
public record PresignResponse(
    UUID mediaId,
    String uploadUrl,
    String storageKey,
    Map<String, String> requiredHeaders,
    long expiresInSeconds
) {
}
