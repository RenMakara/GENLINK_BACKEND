package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface ChallengeService {

    /** Active challenges (no deadline or a future one), each with the user's status. */
    List<ChallengeResponse> getActive(Language language);

    /** Challenges the current user has joined. */
    List<ChallengeResponse> getMine(Language language);

    ChallengeResponse join(UUID challengeId, Language language);

    ChallengeResponse complete(UUID challengeId, Language language);

    /** Claims the reward once: awards XP (via the reward pipeline) and marks CLAIMED. */
    ChallengeResponse claim(UUID challengeId, Language language);
}
