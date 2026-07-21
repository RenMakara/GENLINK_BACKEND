package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwarder;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardServiceImpl implements RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardServiceImpl.class);

    /** XP needed per level. Linear and easy to tune; level = 1 + total_xp / 100. */
    private static final int XP_PER_LEVEL = 100;

    private final XpHistoryRepository xpHistoryRepository;
    private final UserRepository userRepository;
    private final BadgeAwarder badgeAwarder;

    public RewardServiceImpl(XpHistoryRepository xpHistoryRepository,
                             UserRepository userRepository,
                             BadgeAwarder badgeAwarder) {
        this.xpHistoryRepository = xpHistoryRepository;
        this.userRepository = userRepository;
        this.badgeAwarder = badgeAwarder;
    }

    @Override
    @Transactional
    public void award(User user, String sourceType, UUID sourceId, int xp) {
        if (xp <= 0) {
            return;
        }
        int newTotal = user.getTotalXp() + xp;
        int newLevel = 1 + newTotal / XP_PER_LEVEL;
        boolean leveledUp = newLevel > user.getLevel();

        user.setTotalXp(newTotal);
        user.setLevel(newLevel);
        userRepository.save(user);

        xpHistoryRepository.save(XpHistory.builder()
            .user(user).sourceType(sourceType).sourceId(sourceId).xpAmount(xp).build());

        // Badge unlocks are evaluated after the XP lands, so thresholds see the new total.
        badgeAwarder.evaluate(user, sourceType);

        if (leveledUp) {
            log.info("Level up: user={} level={} totalXp={}", user.getId(), newLevel, newTotal);
        }
    }
}
