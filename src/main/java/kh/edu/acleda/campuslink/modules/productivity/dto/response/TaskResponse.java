package kh.edu.acleda.campuslink.modules.productivity.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
    UUID taskId,
    String title,
    String description,
    String priority,
    String category,
    LocalDateTime dueDate,
    Boolean completed
) {
}
