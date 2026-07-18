package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.user.entity.User;

/**
 * Seam the reward pipeline calls after XP lands, so badge unlocks stay decoupled from XP
 * granting. The real implementation (milestone thresholds) arrives with the Badges phase;
 * until then a no-op satisfies the dependency.
 */
public interface BadgeAwarder {

    void evaluate(User user, String sourceType);
}
