package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record FocusSessionResponse(
    UUID sessionId,
    String mode,
    Integer durationMinutes,
    String ambientSound,
    boolean completed,
    int xpEarned,
    LocalDateTime startedAt,
    LocalDateTime endedAt
) {
}
