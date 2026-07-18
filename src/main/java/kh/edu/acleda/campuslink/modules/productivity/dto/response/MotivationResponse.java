package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.util.UUID;

/**
 * @param saved      whether the current user has saved this one
 * @param savedCount how many users saved it (global)
 */
public record MotivationResponse(
    UUID motivationId,
    String title,
    String content,
    String category,
    int savedCount,
    boolean saved
) {
}
