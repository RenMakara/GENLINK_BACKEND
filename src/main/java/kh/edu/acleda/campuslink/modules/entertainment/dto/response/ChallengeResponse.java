package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;

public record ChallengeResponse(
    UUID challengeId,
    String titleEn,
    String titleKm,
    String descriptionEn,
    String descriptionKm,
    ChallengeType challengeType,
    Integer xpReward
) {
}
