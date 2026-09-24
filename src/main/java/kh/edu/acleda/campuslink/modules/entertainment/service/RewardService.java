package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.user.entity.User;

/**
 * The one place XP is granted. Every domain (Community RSVP, quizzes, focus sessions,
 * finance lessons, habits, tasks, challenges) routes through here so that total_xp, the
 * user level, xp_history and badge unlocks stay in one pipeline instead of being
 * reimplemented inline per feature.
 */
public interface RewardService {

    /**
     * Applies a signed XP delta, recomputes the level, records an xp_history row, and checks
     * for badge unlocks. Positive values grant XP; negative values revoke it and floor the
     * user's total at zero. A zero delta is a no-op. Mutates and saves the given user, so the
     * caller's instance reflects the new total/level. Returns the signed delta actually applied;
     * duplicate logical sources return zero.
     */
    int award(User user, String sourceType, UUID sourceId, int xp);
}
