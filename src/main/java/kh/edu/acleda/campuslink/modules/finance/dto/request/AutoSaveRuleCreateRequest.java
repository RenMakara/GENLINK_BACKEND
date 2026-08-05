package kh.edu.acleda.campuslink.modules.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.AutoSaveType;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * Sets up an automatic monthly saving into a goal.
 *
 * @param percent  required for PERCENT_OF_INCOME (e.g. 10.00 = 10%); ignored for ROUND_UP.
 * @param currency USD or KHR; defaults to USD when omitted (and must match the goal's currency).
 */
public record AutoSaveRuleCreateRequest(
    @NotNull UUID savingGoalId,
    @NotNull AutoSaveType type,
    BigDecimal percent,
    Currency currency
) {
}
