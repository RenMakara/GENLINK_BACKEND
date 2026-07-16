package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface MobileHomeBffService {

    MobileHomeResponse getHome(String email, Language language);
}
