package kh.edu.acleda.campuslink.modules.media.entity;

/**
 * How a media object is served.
 *
 * <p>PUBLIC objects get a stable CDN URL (fast, cacheable) and rely on an
 * unguessable UUID key for obscurity. PRIVATE objects have no public URL and are
 * reachable only through a short-lived presigned GET issued per authorized request.
 */
public enum MediaVisibility {
    PUBLIC,
    PRIVATE
}
