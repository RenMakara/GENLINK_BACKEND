package kh.edu.acleda.campuslink.modules.profile.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;

public record SaveItemRequest(
    @NotNull SavedItemType itemType,
    @NotNull UUID itemId
) {
}
