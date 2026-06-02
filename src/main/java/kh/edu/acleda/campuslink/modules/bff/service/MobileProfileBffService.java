package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;

public interface MobileProfileBffService {
    MobileProfileSummaryResponse getProfileSummary(String email);
}
