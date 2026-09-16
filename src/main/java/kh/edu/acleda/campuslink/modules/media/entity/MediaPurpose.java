package kh.edu.acleda.campuslink.modules.media.entity;

/**
 * What a media object is for. Each purpose carries its own policy — the key
 * prefix in the bucket, its default visibility, and whether video is allowed —
 * so the rules live in one place instead of scattered {@code if} branches.
 */
public enum MediaPurpose {
    AVATAR("avatars", MediaVisibility.PUBLIC, false),
    POST("posts", MediaVisibility.PUBLIC, true),
    STORY("stories", MediaVisibility.PUBLIC, true),
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
