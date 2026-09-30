package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.AutoSaveType;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/** Sets up an automatic monthly saving into a goal. */
public record AutoSaveRuleCreateRequest(
    @NotNull UUID savingGoalId,
    @NotNull AutoSaveType type,
    @Digits(integer = 3, fraction = 2) BigDecimal percent,
    Currency currency
) {
}
