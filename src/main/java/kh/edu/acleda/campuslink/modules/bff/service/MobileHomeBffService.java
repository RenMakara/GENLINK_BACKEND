package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface MobileHomeBffService {

    MobileHomeResponse getHome(String email, Language language, Currency currency);

    /** Backward-compatible Java entry point: the home finance card historically used USD. */
    default MobileHomeResponse getHome(String email, Language language) {
        return getHome(email, language, Currency.USD);
    }
}
