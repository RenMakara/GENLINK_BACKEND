package kh.edu.acleda.campuslink.modules.search.dto;

public enum SearchCategory {
    ALL,
    PEOPLE,
    POSTS,
    EVENTS,
    FORUM,
    MEDIA;

    public static SearchCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return SearchCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
