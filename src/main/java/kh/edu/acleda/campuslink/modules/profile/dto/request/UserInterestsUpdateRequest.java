package kh.edu.acleda.campuslink.modules.profile.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Replaces the whole selection, matching the multi-select onboarding step. */
public record UserInterestsUpdateRequest(
    @NotNull @Size(max = 20) List<UUID> interestIds
) {
}
