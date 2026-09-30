package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.user.entity.User;

/** The one place XP is granted. */
public interface RewardService {

    /** Applies a signed XP delta, recomputes the level, records an xp_history row, and checks for badge unlocks. */
    int award(User user, String sourceType, UUID sourceId, int xp);
}
