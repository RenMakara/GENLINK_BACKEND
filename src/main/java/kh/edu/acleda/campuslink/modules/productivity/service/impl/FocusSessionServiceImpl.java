package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.FocusSessionService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FocusSessionServiceImpl implements FocusSessionService {

    private static final String XP_SOURCE = "FOCUS_SESSION";
    private static final int XP_PER_MINUTES = 5;
    private static final int XP_CAP = 30;
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(25, 50, 90);
    private static final Set<FocusSessionStatus> ACTIVE_STATUSES =
        EnumSet.of(FocusSessionStatus.ACTIVE, FocusSessionStatus.PAUSED);

    private final FocusSessionRepository focusSessionRepository;
    private final RewardService rewardService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public FocusSessionServiceImpl(FocusSessionRepository focusSessionRepository,
                                   RewardService rewardService,
                                   UserRepository userRepository,
                                   CurrentUserProvider currentUserProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.rewardService = rewardService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public FocusSessionResponse start(FocusSessionStartRequest request) {
        validateStart(request);
        UUID userId = currentUserProvider.getCurrentUserId();
        // Serialize starts for this user before checking for an active row. The partial
        // unique index remains the final guard if another writer bypasses this service.
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        FocusSession existing = focusSessionRepository
            .findFirstByUserIdAndStatusInOrderByStartedAtDesc(userId, ACTIVE_STATUSES)
            .orElse(null);

        if (existing != null) {
            normalize(existing);
            LocalDateTime now = LocalDateTime.now();
            if (remainingSeconds(existing, now) == 0) {
                return toResponse(completeInternal(existing, now));
            }
            return toResponse(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        FocusSession session = focusSessionRepository.save(FocusSession.builder()
            .user(user)
            .mode(normalizeMode(request.mode()))
            .durationMinutes(request.durationMinutes())
            // Sound is intentionally not part of the deadline MVP.
            .ambientSound(null)
            .completed(false)
            .status(FocusSessionStatus.ACTIVE)
            .xpEarned(0)
            .focusedSeconds(0)
            .startedAt(now)
            .activeStartedAt(now)
            .build());
        return toResponse(session);
    }

    @Override
    @Transactional
    public FocusSessionResponse active() {
        UUID userId = currentUserProvider.getCurrentUserId();
        FocusSession session = focusSessionRepository
            .findFirstByUserIdAndStatusInOrderByStartedAtDesc(userId, ACTIVE_STATUSES)
            .orElse(null);
        if (session == null) {
            return null;
        }

        normalize(session);
        LocalDateTime now = LocalDateTime.now();
        if (remainingSeconds(session, now) == 0) {
            return toResponse(completeInternal(session, now));
        }
        return toResponse(session);
    }

    @Override
    @Transactional
    public FocusSessionResponse pause(UUID id) {
        FocusSession session = loadOwnedForUpdate(id);
        normalize(session);
        if (session.getStatus() == FocusSessionStatus.PAUSED) {
            return toResponse(session);
        }
        requireActive(session);

        LocalDateTime now = LocalDateTime.now();
        session.setFocusedSeconds(currentFocusedSeconds(session, now));
        session.setActiveStartedAt(null);
        session.setPausedAt(now);
        session.setStatus(FocusSessionStatus.PAUSED);
        session.setCompleted(false);
        return toResponse(focusSessionRepository.save(session));
    }

    @Override
    @Transactional
    public FocusSessionResponse resume(UUID id) {
        FocusSession session = loadOwnedForUpdate(id);
        normalize(session);
        if (session.getStatus() == FocusSessionStatus.ACTIVE) {
            return toResponse(session);
        }
        requirePaused(session);

        LocalDateTime now = LocalDateTime.now();
        session.setPausedAt(null);
        session.setActiveStartedAt(now);
        session.setStatus(FocusSessionStatus.ACTIVE);
        session.setCompleted(false);
        return toResponse(focusSessionRepository.save(session));
    }

    @Override
    @Transactional
    public FocusSessionResponse complete(UUID id) {
        FocusSession session = loadOwnedForUpdate(id);
        normalize(session);
        if (session.getStatus() == FocusSessionStatus.COMPLETED) {
            return toResponse(session);
        }
        if (session.getStatus() == FocusSessionStatus.ABANDONED) {
            throw new ApiException(
                "Abandoned focus sessions cannot be completed", "FOCUS_SESSION_TERMINAL");
        }
        return toResponse(completeInternal(session, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public FocusSessionResponse abandon(UUID id) {
        FocusSession session = loadOwnedForUpdate(id);
        normalize(session);
        if (session.getStatus() == FocusSessionStatus.ABANDONED
            || session.getStatus() == FocusSessionStatus.COMPLETED) {
            return toResponse(session);
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == FocusSessionStatus.ACTIVE) {
            session.setFocusedSeconds(currentFocusedSeconds(session, now));
        }
        session.setStatus(FocusSessionStatus.ABANDONED);
        session.setCompleted(false);
        session.setXpEarned(0);
        session.setActiveStartedAt(null);
        session.setPausedAt(null);
        session.setEndedAt(now);
        return toResponse(focusSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FocusSessionResponse> history(Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(
            focusSessionRepository.findByUserIdAndCompletedTrueOrderByStartedAtDesc(userId, pageable),
            this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FocusStatsResponse stats() {
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate today = LocalDate.now();
        List<FocusSession> completed = focusSessionRepository.findByUserIdAndCompletedTrue(userId);

        long totalSessions = completed.size();
        long totalMinutes = completed.stream().mapToLong(this::focusedMinutes).sum();
        long totalXp = completed.stream().mapToLong(s -> orZero(s.getXpEarned())).sum();

        List<FocusSession> todays = completed.stream()
            .filter(s -> s.getStartedAt() != null && s.getStartedAt().toLocalDate().equals(today))
            .toList();
        int todayMinutes = todays.stream().mapToInt(this::focusedMinutes).sum();

        return new FocusStatsResponse(todays.size(), todayMinutes, totalSessions, totalMinutes, totalXp);
    }

    private FocusSession completeInternal(FocusSession session, LocalDateTime now) {
        normalize(session);
        int focusedSeconds = currentFocusedSeconds(session, now);
        int xp = xpFor(focusedSeconds);
        session.setFocusedSeconds(focusedSeconds);
        session.setStatus(FocusSessionStatus.COMPLETED);
        session.setCompleted(true);
        session.setEndedAt(now);
        session.setActiveStartedAt(null);
        session.setPausedAt(null);
        session.setXpEarned(xp);
        focusSessionRepository.save(session);
        awardXp(session, xp);
        return session;
    }

    private int xpFor(int focusedSeconds) {
        int minutes = focusedSeconds / 60;
        return Math.min(XP_CAP, minutes / XP_PER_MINUTES);
    }

    private void awardXp(FocusSession session, int xp) {
        if (xp <= 0) {
            return;
        }
        rewardService.award(loadCurrentUser(), XP_SOURCE, session.getId(), xp);
    }

    private FocusSessionResponse toResponse(FocusSession session) {
        normalize(session);
        LocalDateTime now = LocalDateTime.now();
        int focusedSeconds = currentFocusedSeconds(session, now);
        return new FocusSessionResponse(
            session.getId(),
            session.getMode(),
            session.getDurationMinutes(),
            session.getAmbientSound(),
            session.getStatus(),
            session.getStatus() == FocusSessionStatus.COMPLETED,
            orZero(session.getXpEarned()),
            focusedSeconds,
            remainingSeconds(session, now),
            session.getStartedAt(),
            session.getActiveStartedAt(),
            session.getPausedAt(),
            session.getEndedAt());
    }

    private int currentFocusedSeconds(FocusSession session, LocalDateTime now) {
        normalize(session);
        long focused = Math.max(0, (long) orZero(session.getFocusedSeconds()));
        if (session.getStatus() == FocusSessionStatus.ACTIVE) {
            LocalDateTime activeStartedAt = session.getActiveStartedAt() == null
                ? session.getStartedAt() : session.getActiveStartedAt();
            if (activeStartedAt != null && now.isAfter(activeStartedAt)) {
                focused += Duration.between(activeStartedAt, now).getSeconds();
            }
        }
        long planned = Math.max(0L, (long) orZero(session.getDurationMinutes()) * 60L);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(focused, planned));
    }

    private int remainingSeconds(FocusSession session, LocalDateTime now) {
        int planned = Math.max(0, orZero(session.getDurationMinutes()) * 60);
        return Math.max(0, planned - currentFocusedSeconds(session, now));
    }

    private int focusedMinutes(FocusSession session) {
        return orZero(session.getFocusedSeconds()) / 60;
    }

    private void normalize(FocusSession session) {
        if (session.getStatus() == null) {
            session.setStatus(Boolean.TRUE.equals(session.getCompleted())
                ? FocusSessionStatus.COMPLETED : FocusSessionStatus.ACTIVE);
        }
        if (session.getFocusedSeconds() == null) {
            session.setFocusedSeconds(session.getStatus() == FocusSessionStatus.COMPLETED
                ? Math.max(0, orZero(session.getDurationMinutes()) * 60) : 0);
        }
        if (session.getXpEarned() == null) {
            session.setXpEarned(0);
        }
        if (session.getStatus() == FocusSessionStatus.ACTIVE
            && session.getActiveStartedAt() == null) {
            session.setActiveStartedAt(session.getStartedAt());
        }
    }

    private void validateStart(FocusSessionStartRequest request) {
        if (request == null || request.durationMinutes() == null
            || !ALLOWED_DURATIONS.contains(request.durationMinutes())) {
            throw new ApiException(
                "Focus duration must be 25, 50, or 90 minutes", "FOCUS_INVALID_DURATION");
        }
        if (request.mode() != null && !request.mode().isBlank()
            && !Set.of("POMODORO", "DEEP_FOCUS").contains(request.mode().toUpperCase())) {
            throw new ApiException("Unsupported focus mode", "FOCUS_INVALID_MODE");
        }
    }

    private String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? "POMODORO" : mode.toUpperCase();
    }

    private void requireActive(FocusSession session) {
        if (session.getStatus() != FocusSessionStatus.ACTIVE) {
            throw new ApiException("Focus session is not active", "FOCUS_INVALID_STATE");
        }
    }

    private void requirePaused(FocusSession session) {
        if (session.getStatus() != FocusSessionStatus.PAUSED) {
            throw new ApiException("Focus session is not paused", "FOCUS_INVALID_STATE");
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private FocusSession loadOwnedForUpdate(UUID id) {
        return focusSessionRepository
            .findByIdAndUserIdForUpdate(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Focus session not found"));
    }

}
