package kh.edu.acleda.campuslink.modules.finance.enums;

/**
 * How an auto-save rule works out how much to move into the goal each month.
 *
 * <p>PERCENT_OF_INCOME: a fixed percent of last month's income.
 * ROUND_UP: the spare change from rounding each of last month's expenses up to the next whole unit.
 */
public enum AutoSaveType {
    PERCENT_OF_INCOME,
    ROUND_UP
}
