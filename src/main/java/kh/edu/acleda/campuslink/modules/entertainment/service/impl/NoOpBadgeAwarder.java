package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwarder;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * Placeholder until the Badges phase provides the real BadgeAwarder (milestone
 * thresholds). Delete this class when the real BadgeService implements BadgeAwarder.
 */
@Service
public class NoOpBadgeAwarder implements BadgeAwarder {

    @Override
    public void evaluate(User user, String sourceType) {
        // no-op
    }
}
