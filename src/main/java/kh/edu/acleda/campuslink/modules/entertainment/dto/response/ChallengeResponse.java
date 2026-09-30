package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeStatus;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;

/** @param status the current user's status for this challenge (NOT_STARTED if not joined) */
public record ChallengeResponse(
    UUID challengeId,
    String title,
    String description,
    ChallengeType challengeType,
    Integer xpReward,
    LocalDateTime deadline,
    ChallengeStatus status,
    int progress
) {
}
