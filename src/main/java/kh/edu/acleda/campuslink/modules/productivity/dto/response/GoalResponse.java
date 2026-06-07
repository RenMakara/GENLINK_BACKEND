package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
    UUID goalId,
    String title,
    String goalType,
    BigDecimal progressPercentage,
    String status,
    LocalDate deadline
) {
}
