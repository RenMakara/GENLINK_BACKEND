package kh.edu.acleda.campuslink.modules.media.entity;

/** What a media object is for. */
public enum MediaPurpose {
    AVATAR("avatars", MediaVisibility.PUBLIC, false),
    POST("posts", MediaVisibility.PRIVATE, true),
    STORY("stories", MediaVisibility.PRIVATE, true),
    SHORT("short", MediaVisibility.PUBLIC, true),
    EVENT_COVER("events", MediaVisibility.PUBLIC, false),
    COMMUNITY_COVER("communities", MediaVisibility.PUBLIC, false),
    PORTFOLIO("portfolio", MediaVisibility.PUBLIC, false);

    private final String keyPrefix;
    private final MediaVisibility defaultVisibility;
    private final boolean allowsVideo;

    MediaPurpose(String keyPrefix, MediaVisibility defaultVisibility, boolean allowsVideo) {
        this.keyPrefix = keyPrefix;
        this.defaultVisibility = defaultVisibility;
        this.allowsVideo = allowsVideo;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public MediaVisibility defaultVisibility() {
        return defaultVisibility;
    }

    public boolean allowsVideo() {
        return allowsVideo;
    }
}
