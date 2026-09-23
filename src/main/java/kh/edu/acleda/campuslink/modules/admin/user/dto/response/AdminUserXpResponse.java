package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.response.PageResponse;

/** Read-only XP, level, ledger and earned-badge projection for the admin user detail page. */
public record AdminUserXpResponse(
    UUID userId,
    int totalXp,
    int level,
    int xpIntoLevel,
    int xpToNextLevel,
    int levelProgressPercent,
    List<SourceBreakdown> breakdown,
    PageResponse<LedgerEntry> ledger,
    List<EarnedBadge> badges
) {

    /** Signed XP total and entry count for one {@code xp_history.source_type}. */
    public record SourceBreakdown(
        String sourceType,
        long xpAmount,
        long entryCount
    ) {
    }

    /** Safe, newest-first ledger row; negative XP amounts are preserved. */
    public record LedgerEntry(
        UUID xpHistoryId,
        String sourceType,
        UUID sourceId,
        int xpAmount,
        Instant timestamp
    ) {
    }

    /** Safe projection of a badge already unlocked by the target user. */
    public record EarnedBadge(
        UUID badgeId,
        String code,
        String nameEn,
        String nameKm,
        String descriptionEn,
        String descriptionKm,
        String requirement,
        String iconUrl,
        LocalDateTime unlockedAt
    ) {
    }
}
