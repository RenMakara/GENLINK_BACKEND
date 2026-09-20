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
     * Adds xp to the user, recomputes their level, records an xp_history row, and checks
     * for badge unlocks. A non-positive xp is a no-op. Mutates and saves the given
     * (managed) user, so the caller's instance reflects the new total/level. Returns
     * the XP actually awarded; duplicate logical sources return zero.
     */
    int award(User user, String sourceType, UUID sourceId, int xp);
}
