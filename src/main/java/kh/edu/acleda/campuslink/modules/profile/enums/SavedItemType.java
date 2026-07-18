package kh.edu.acleda.campuslink.modules.profile.enums;

/**
 * What a saved_items row points at. Kept as an enum rather than free text so that
 * "POST" and "post" cannot both be stored - the unique (user_id, item_type,
 * item_id) constraint would treat them as different items.
 */
public enum SavedItemType {
    POST,
    DISCUSSION,
    EVENT,
    OPPORTUNITY,
    MOTIVATION,
    LESSON,
    SHORT_CONTENT
}
