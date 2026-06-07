package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record HabitResponse(
    UUID habitId,
    String title,
    String category,
    String frequency,
    Integer streakCount,
    BigDecimal completionRate
) {
}
