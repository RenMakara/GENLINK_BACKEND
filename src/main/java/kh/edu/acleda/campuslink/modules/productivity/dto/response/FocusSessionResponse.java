package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;

public record FocusSessionResponse(
    UUID sessionId,
    String mode,
    Integer durationMinutes,
    String ambientSound,
    FocusSessionStatus status,
    boolean completed,
    int xpEarned,
    int focusedSeconds,
    int remainingSeconds,
    LocalDateTime startedAt,
    LocalDateTime activeStartedAt,
    LocalDateTime pausedAt,
    LocalDateTime endedAt
) {
}
