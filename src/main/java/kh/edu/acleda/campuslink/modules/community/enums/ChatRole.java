package kh.edu.acleda.campuslink.modules.community.enums;

/**
 * A member's role in a chat room (groups; private rooms are all MEMBER).
 *
 * <p>OWNER is the creator — cannot be removed or demoted, and is the only one who can change
 * roles. ADMIN manages members, renames the group, and pins. MEMBER just participates.
 */
public enum ChatRole {
    OWNER,
    ADMIN,
    MEMBER
}
