package kh.edu.acleda.campuslink.modules.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Confirm that the client finished uploading, with dimensions/duration read from
 * the local file. All three optional fields are null for media where they do not
 * apply (e.g. duration on an image).
 */
public record ConfirmRequest(
    @NotNull java.util.UUID mediaId,
    @Positive Integer width,
    @Positive Integer height,
    @PositiveOrZero Long durationMs
) {
}
