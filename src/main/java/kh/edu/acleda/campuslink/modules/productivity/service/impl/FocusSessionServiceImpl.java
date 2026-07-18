package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.FocusSessionService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FocusSessionServiceImpl implements FocusSessionService {

    private static final Logger log = LoggerFactory.getLogger(FocusSessionServiceImpl.class);

    private static final String XP_SOURCE = "FOCUS_SESSION";
    private static final int XP_PER_MINUTES = 5;   // 1 XP per 5 focused minutes
    private static final int XP_CAP = 30;          // capped so a huge duration can't farm XP

    private final FocusSessionRepository focusSessionRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public FocusSessionServiceImpl(FocusSessionRepository focusSessionRepository,
                                   XpHistoryRepository xpHistoryRepository,
                                   UserRepository userRepository,
                                   CurrentUserProvider currentUserProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.xpHistoryRepository = xpHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public FocusSessionResponse start(FocusSessionStartRequest request) {
        FocusSession session = focusSessionRepository.save(FocusSession.builder()
            .user(loadCurrentUser())
            .mode(request.mode())
            .durationMinutes(request.durationMinutes())
            .ambientSound(request.ambientSound())
            .completed(false)
            .xpEarned(0)
            .startedAt(LocalDateTime.now())
            .build());
        return toResponse(session);
    }

    @Override
    @Transactional
    public FocusSessionResponse complete(UUID id) {
        FocusSession session = loadOwned(id);
        // Idempotent: completing an already-completed session must not pay XP twice.
        if (Boolean.TRUE.equals(session.getCompleted())) {
            return toResponse(session);
        }

        int xp = xpFor(session.getDurationMinutes());
        session.setCompleted(true);
        session.setEndedAt(LocalDateTime.now());
        session.setXpEarned(xp);
        focusSessionRepository.save(session);
        awardXp(session, xp);
        return toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FocusSessionResponse> history(Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(
            focusSessionRepository.findByUserIdOrderByStartedAtDesc(userId, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FocusStatsResponse stats() {
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate today = LocalDate.now();
        List<FocusSession> completed = focusSessionRepository.findByUserIdAndCompletedTrue(userId);

        long totalSessions = completed.size();
        long totalMinutes = completed.stream().mapToLong(s -> orZero(s.getDurationMinutes())).sum();
        long totalXp = completed.stream().mapToLong(s -> orZero(s.getXpEarned())).sum();

        List<FocusSession> todays = completed.stream()
            .filter(s -> s.getStartedAt() != null && s.getStartedAt().toLocalDate().equals(today))
            .toList();
        int todayMinutes = todays.stream().mapToInt(s -> orZero(s.getDurationMinutes())).sum();

        return new FocusStatsResponse(todays.size(), todayMinutes, totalSessions, totalMinutes, totalXp);
    }

    private int xpFor(Integer durationMinutes) {
        int minutes = durationMinutes == null ? 0 : durationMinutes;
        return Math.min(XP_CAP, minutes / XP_PER_MINUTES);
    }

    private void awardXp(FocusSession session, int xp) {
        if (xp <= 0) {
            return;
        }
        User user = loadCurrentUser();
        user.setTotalXp(user.getTotalXp() + xp);
        userRepository.save(user);
        xpHistoryRepository.save(XpHistory.builder()
            .user(user).sourceType(XP_SOURCE).sourceId(session.getId()).xpAmount(xp).build());
        log.info("Focus session XP: user={} session={} minutes={} xp={}",
            user.getId(), session.getId(), session.getDurationMinutes(), xp);
    }

    private FocusSessionResponse toResponse(FocusSession s) {
        return new FocusSessionResponse(s.getId(), s.getMode(), s.getDurationMinutes(), s.getAmbientSound(),
            Boolean.TRUE.equals(s.getCompleted()), orZero(s.getXpEarned()), s.getStartedAt(), s.getEndedAt());
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private FocusSession loadOwned(UUID id) {
        return focusSessionRepository.findByIdAndUserId(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Focus session not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
