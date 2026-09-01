package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

public interface MobileFinanceBffService {

    /** The whole Finance screen for the given user and currency (resolved by email). */
    MobileFinanceDashboardResponse getFinanceDashboard(String email, Currency currency);

    /** Backward-compatible Java entry point: dashboards historically defaulted to USD. */
    default MobileFinanceDashboardResponse getFinanceDashboard(String email) {
        return getFinanceDashboard(email, Currency.USD);
    }
}
