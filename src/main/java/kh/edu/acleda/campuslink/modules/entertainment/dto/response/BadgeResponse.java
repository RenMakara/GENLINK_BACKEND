package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param earned     whether the current user has unlocked this badge
 * @param unlockedAt when they unlocked it, or null if not earned
 */
public record BadgeResponse(
    UUID badgeId,
    String name,
    String description,
    String requirement,
    String iconUrl,
    boolean earned,
    LocalDateTime unlockedAt
) {
}
