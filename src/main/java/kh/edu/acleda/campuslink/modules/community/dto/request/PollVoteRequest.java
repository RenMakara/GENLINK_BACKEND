package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Casts (or moves) the caller's single vote to {@code optionId}. */
public record PollVoteRequest(
    @NotNull UUID optionId
) {
}
