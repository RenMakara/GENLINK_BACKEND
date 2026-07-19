package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwarder;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-unlocks the named milestone badges after XP lands. Rules are keyed by the badge's
 * `code`, evaluated against xp_history counts per source and the user's total XP. Runs
 * inside the reward transaction, so an unlock is committed with the XP that earned it.
 */
@Service
public class BadgeAwarderImpl implements BadgeAwarder {

    private static final Logger log = LoggerFactory.getLogger(BadgeAwarderImpl.class);

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final XpHistoryRepository xpHistoryRepository;

    public BadgeAwarderImpl(BadgeRepository badgeRepository,
                            UserBadgeRepository userBadgeRepository,
                            XpHistoryRepository xpHistoryRepository) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.xpHistoryRepository = xpHistoryRepository;
    }

    @Override
    @Transactional
    public void evaluate(User user, String sourceType) {
        UUID userId = user.getId();
        List<Badge> coded = badgeRepository.findByDeletedAtIsNullOrderByCreatedAtAsc().stream()
            .filter(b -> b.getCode() != null)
            .toList();

        Map<String, Long> counts = new HashMap<>(); // source_type -> count, cached per call
        for (Badge badge : coded) {
            if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
                continue;
            }
            if (qualifies(badge.getCode(), user, userId, counts)) {
                unlock(user, badge);
            }
        }
    }

    private boolean qualifies(String code, User user, UUID userId, Map<String, Long> counts) {
        return switch (code) {
            case "FINANCIAL_STARTER" -> count(userId, "FINANCE_LESSON", counts) >= 1;
            case "QUIZ_CHAMPION" -> count(userId, "QUIZ", counts) >= 3;
            case "EVENT_EXPLORER" -> count(userId, "EVENT_RSVP", counts) >= 3;
            case "CONSISTENCY_MASTER" -> count(userId, "HABIT", counts) >= 7;
            case "PRODUCTIVITY_HERO" -> count(userId, "HABIT", counts)
                + count(userId, "TASK", counts) + count(userId, "FOCUS_SESSION", counts) >= 10;
            // No community-engagement XP source yet, so use total XP as a proxy for now.
            case "COMMUNICATION_HELPER" -> user.getTotalXp() >= 300;
            default -> false;
        };
    }

    private long count(UUID userId, String source, Map<String, Long> cache) {
        return cache.computeIfAbsent(source, s -> xpHistoryRepository.countByUserIdAndSourceType(userId, s));
    }

    private void unlock(User user, Badge badge) {
        try {
            userBadgeRepository.saveAndFlush(UserBadge.builder()
                .user(user).badge(badge).unlockedAt(LocalDateTime.now()).build());
            log.info("Badge unlocked: user={} badge={}", user.getId(), badge.getCode());
        } catch (DataIntegrityViolationException concurrentUnlock) {
            // A concurrent award won the UNIQUE(user, badge) race; nothing to do.
        }
    }
}
