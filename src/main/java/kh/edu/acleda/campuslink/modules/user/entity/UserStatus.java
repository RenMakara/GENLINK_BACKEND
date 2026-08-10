package kh.edu.acleda.campuslink.modules.user.entity;

/**
 * Mirror of {@link AccountStatus}, converted by name in {@code User.getStatus()}.
 *
 * <p><strong>Every value of {@link AccountStatus} must exist here.</strong> The conversion is
 * {@code UserStatus.valueOf(accountStatus.name())}, so a value present in one enum and missing
 * from the other throws {@code IllegalArgumentException} at runtime - and only for the users
 * in that state. {@link AccountStatus#SUSPENDED} was added here for exactly that reason.
 *
 * <p>Nothing outside {@code User} uses this today: the accessors have no callers. The two
 * enums are a duplication worth collapsing when someone has reason to touch this area.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    BANNED,
    SUSPENDED,
    DELETED
}
