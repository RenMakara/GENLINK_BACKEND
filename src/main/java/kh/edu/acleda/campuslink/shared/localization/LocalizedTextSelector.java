package kh.edu.acleda.campuslink.shared.localization;

import kh.edu.acleda.campuslink.shared.enums.Language;

public final class LocalizedTextSelector {

    private LocalizedTextSelector() {
    }

    public static String select(String enValue, String kmValue, Language language) {
        if (language == Language.KHMER) {
            return firstNonBlank(kmValue, enValue);
        }
        return firstNonBlank(enValue, kmValue);
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }
}
