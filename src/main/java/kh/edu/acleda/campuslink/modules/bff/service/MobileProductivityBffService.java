package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface MobileProductivityBffService {

    /** The whole Productivity screen for the given user (resolved by email). */
    MobileProductivityDashboardResponse getDashboard(String email, Language language);
}
