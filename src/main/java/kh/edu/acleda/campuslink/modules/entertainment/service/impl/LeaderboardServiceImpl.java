package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse.Entry;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse.Me;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.LeaderboardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final int TOP_N = 20;

    private final XpHistoryRepository xpHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public LeaderboardServiceImpl(XpHistoryRepository xpHistoryRepository,
                                  UserRepository userRepository,
                                  CurrentUserProvider currentUserProvider) {
        this.xpHistoryRepository = xpHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public LeaderboardResponse getLeaderboard(LeaderboardCategory category, LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.FRIENDS) {
            throw new ApiException("Friends leaderboard is not available yet", "NOT_IMPLEMENTED");
        }
        Instant since = since(period);
        List<Object[]> rows = category == LeaderboardCategory.OVERALL_XP
            ? xpHistoryRepository.leaderboardOverall(since)
            : xpHistoryRepository.leaderboardByCategory(since, sourcesFor(category));

        Me me = myStanding(rows);
        List<Entry> entries = topEntries(rows);
        return new LeaderboardResponse(category, period, entries, me);
    }

    private Me myStanding(List<Object[]> rows) {
        UUID meId = currentUserProvider.getCurrentUserId();
        for (int i = 0; i < rows.size(); i++) {
            if (meId.equals(rows.get(i)[0])) {
                return new Me(i + 1, points(rows.get(i)));
            }
        }
        return new Me(null, 0);
    }

    private List<Entry> topEntries(List<Object[]> rows) {
        List<Object[]> top = rows.stream().limit(TOP_N).toList();
        List<UUID> ids = top.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, User> users = userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            UUID userId = (UUID) top.get(i)[0];
            User u = users.get(userId);
            entries.add(new Entry(i + 1, userId,
                u == null ? null : u.getFullName(),
                u == null ? null : u.getAvatarUrl(),
                u == null ? 0 : u.getLevel(),
                points(top.get(i))));
        }
        return entries;
    }

    private Instant since(LeaderboardPeriod period) {
        Instant now = Instant.now();
        return switch (period) {
            case WEEKLY -> now.minus(7, ChronoUnit.DAYS);
            case MONTHLY -> now.minus(30, ChronoUnit.DAYS);
            default -> Instant.EPOCH; // ALL_TIME
        };
    }

    private Collection<String> sourcesFor(LeaderboardCategory category) {
        return switch (category) {
            case PRODUCTIVITY -> List.of("HABIT", "TASK", "FOCUS_SESSION");
            case COMMUNITY_ENGAGEMENT -> List.of("EVENT_RSVP");
            case FINANCIAL_LEARNING -> List.of("FINANCE_LESSON");
            case QUIZ_PERFORMANCE -> List.of("QUIZ");
            default -> List.of(); // OVERALL_XP handled separately
        };
    }

    private long points(Object[] row) {
        return row[1] == null ? 0 : ((Number) row[1]).longValue();
    }
}
