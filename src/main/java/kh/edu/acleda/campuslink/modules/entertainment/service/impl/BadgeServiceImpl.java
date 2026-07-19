package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BadgeServiceImpl implements BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final CurrentUserProvider currentUserProvider;

    public BadgeServiceImpl(BadgeRepository badgeRepository,
                            UserBadgeRepository userBadgeRepository,
                            CurrentUserProvider currentUserProvider) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeResponse> getAll(Language language) {
        Map<UUID, LocalDateTime> earned = earnedByBadgeId();
        return badgeRepository.findByDeletedAtIsNullOrderByCreatedAtAsc().stream()
            .map(b -> toResponse(b, earned.get(b.getId()), language))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeResponse> getMine(Language language) {
        return userBadgeRepository.findByUserIdOrderByUnlockedAtDesc(currentUserProvider.getCurrentUserId()).stream()
            .filter(ub -> ub.getBadge() != null && ub.getBadge().getDeletedAt() == null)
            .map(ub -> toResponse(ub.getBadge(), ub.getUnlockedAt(), language))
            .toList();
    }

    private Map<UUID, LocalDateTime> earnedByBadgeId() {
        Map<UUID, LocalDateTime> map = new HashMap<>();
        for (UserBadge ub : userBadgeRepository
                .findByUserIdOrderByUnlockedAtDesc(currentUserProvider.getCurrentUserId())) {
            if (ub.getBadge() != null) {
                map.put(ub.getBadge().getId(), ub.getUnlockedAt());
            }
        }
        return map;
    }

    private BadgeResponse toResponse(Badge b, LocalDateTime unlockedAt, Language language) {
        return new BadgeResponse(b.getId(),
            LocalizedTextSelector.select(b.getNameEn(), b.getNameKm(), language),
            LocalizedTextSelector.select(b.getDescriptionEn(), b.getDescriptionKm(), language),
            b.getRequirement(), b.getIconUrl(), unlockedAt != null, unlockedAt);
    }
}
