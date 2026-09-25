package kh.edu.acleda.campuslink.modules.admin.analytics.dto.request;

import java.util.Locale;
import kh.edu.acleda.campuslink.shared.exception.ApiException;

/** The nine stable analytics series that can be exported. */
public enum AdminAnalyticsSeries {
    USER_GROWTH("user-growth"),
    ACTIVE_USERS("active-users"),
    REGISTRATIONS("registrations"),
    USER_STATUS("user-status"),
    UNIVERSITIES("universities"),
    WORK_STATUS("work-status"),
    XP_DISTRIBUTION("xp-distribution"),
    LANGUAGES("languages"),
    PLATFORMS("platforms");

    private final String wireValue;

    AdminAnalyticsSeries(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AdminAnalyticsSeries fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AdminAnalyticsSeries series : values()) {
            if (series.wireValue.equals(normalized)) {
                return series;
            }
        }
        throw invalid();
    }

    private static ApiException invalid() {
        return new ApiException(
            "series must be one of user-growth, active-users, registrations, user-status, "
                + "universities, work-status, xp-distribution, languages, or platforms",
            "VALIDATION_ERROR");
    }
}
