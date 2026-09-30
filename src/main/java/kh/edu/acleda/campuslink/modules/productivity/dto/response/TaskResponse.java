package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** @param overdue true when the task has a past due date and is not yet completed - */
public record TaskResponse(
    UUID taskId,
    String title,
    String description,
    String priority,
    String category,
    LocalDateTime dueDate,
    LocalDateTime reminderTime,
    boolean completed,
    boolean overdue,
    int xpEarned
) {
}
