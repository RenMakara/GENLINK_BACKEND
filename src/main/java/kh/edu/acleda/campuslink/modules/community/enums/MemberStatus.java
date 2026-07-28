package kh.edu.acleda.campuslink.modules.community.enums;

public enum MemberStatus {
    ACTIVE,
    /** Requested to join a PRIVATE community and awaiting owner/admin approval. Not yet a member. */
    PENDING,
    LEFT,
    REMOVED
}
