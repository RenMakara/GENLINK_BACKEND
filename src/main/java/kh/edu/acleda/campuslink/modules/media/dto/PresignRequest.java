package kh.edu.acleda.campuslink.modules.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import kh.edu.acleda.campuslink.modules.media.entity.MediaPurpose;

/** Ask for a presigned upload URL. */
public record PresignRequest(
    @NotBlank String contentType,
    @Positive long sizeBytes,
    @NotNull MediaPurpose purpose,
    @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$", message = "checksumSha256 must be 64 lowercase hex chars")
    String checksumSha256
) {
}
