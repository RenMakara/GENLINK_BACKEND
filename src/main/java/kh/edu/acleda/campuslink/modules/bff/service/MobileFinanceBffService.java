package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;

public interface MobileFinanceBffService {

    /** The whole Finance screen for the given user (resolved by email). */
    MobileFinanceDashboardResponse getFinanceDashboard(String email);
}
