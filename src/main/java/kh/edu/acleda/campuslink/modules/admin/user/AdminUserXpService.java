package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, owner-scoped XP projection for the admin user detail page. */
@Service
public class AdminUserXpService {

    private static final int XP_PER_LEVEL = 100;
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);

    private final UserRepository userRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final UserBadgeRepository userBadgeRepository;

    public AdminUserXpService(
        UserRepository userRepository,
        XpHistoryRepository xpHistoryRepository,
        UserBadgeRepository userBadgeRepository) {
        this.userRepository = userRepository;
        this.xpHistoryRepository = xpHistoryRepository;
        this.userBadgeRepository = userBadgeRepository;
    }

    @Transactional(readOnly = true)
    public AdminUserXpResponse get(UUID userId, int page, int size) {
        User user = requireUser(userId);
        Pageable pageable = pageRequest(page, size);

        int totalXp = user.getTotalXp();
        int level = 1 + totalXp / XP_PER_LEVEL;
        int levelStartXp = (level - 1) * XP_PER_LEVEL;
        int xpIntoLevel = totalXp - levelStartXp;
        int xpToNextLevel = level * XP_PER_LEVEL - totalXp;
        int levelProgressPercent = Math.max(0, Math.min(99, xpIntoLevel));

        List<AdminUserXpResponse.SourceBreakdown> breakdown = xpHistoryRepository
            .summarizeByUserId(userId)
            .stream()
            .map(AdminUserXpService::toBreakdown)
            .toList();
        Page<XpHistory> ledger = xpHistoryRepository.findPageByUserId(userId, pageable);
        PageResponse<AdminUserXpResponse.LedgerEntry> ledgerResponse = PageResponse.from(
            ledger, AdminUserXpService::toLedgerEntry);
        List<AdminUserXpResponse.EarnedBadge> badges = userBadgeRepository
            .findByUserIdOrderByUnlockedAtDesc(userId)
            .stream()
            .filter(userBadge -> userBadge.getBadge() != null)
            .filter(userBadge -> userBadge.getBadge().getDeletedAt() == null)
            .map(AdminUserXpService::toEarnedBadge)
            .toList();

        return new AdminUserXpResponse(
            userId,
            totalXp,
            level,
            xpIntoLevel,
            xpToNextLevel,
            levelProgressPercent,
            breakdown,
            ledgerResponse,
            badges
        );
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw invalid("page must be zero or greater");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw invalid("size must be one of 25, 50, or 100");
        }
        return PageRequest.of(page, size);
    }

    private static ApiException invalid(String message) {
        return new ApiException(message, "VALIDATION_ERROR");
    }

    private static AdminUserXpResponse.SourceBreakdown toBreakdown(
        XpHistoryRepository.SourceBreakdownProjection projection) {
        return new AdminUserXpResponse.SourceBreakdown(
            projection.getSourceType(),
            projection.getXpAmount() == null ? 0 : projection.getXpAmount(),
            projection.getEntryCount() == null ? 0 : projection.getEntryCount()
        );
    }

    private static AdminUserXpResponse.LedgerEntry toLedgerEntry(XpHistory entry) {
        return new AdminUserXpResponse.LedgerEntry(
            entry.getId(),
            entry.getSourceType(),
            entry.getSourceId(),
            entry.getXpAmount(),
            entry.getCreatedAt()
        );
    }

    private static AdminUserXpResponse.EarnedBadge toEarnedBadge(UserBadge userBadge) {
        Badge badge = userBadge.getBadge();
        return new AdminUserXpResponse.EarnedBadge(
            badge.getId(),
            badge.getCode(),
            badge.getNameEn(),
            badge.getNameKm(),
            badge.getDescriptionEn(),
            badge.getDescriptionKm(),
            badge.getRequirement(),
            badge.getIconUrl(),
            userBadge.getUnlockedAt()
        );
    }
}
