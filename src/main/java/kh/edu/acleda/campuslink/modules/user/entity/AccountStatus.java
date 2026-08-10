package kh.edu.acleda.campuslink.modules.user.entity;

/**
 * Lifecycle state of a user account.
 *
 * <p>Stored as a constraint-free {@code VARCHAR(30)} (migration 0001 declares no CHECK), which
 * is why {@link #SUSPENDED} could be added without a migration - the same call already made
 * for {@code MemberStatus.PENDING}.
 *
 * <p>{@code CustomUserDetailsService} treats this as an <strong>allow-list</strong>: only
 * {@link #ACTIVE} is enabled. A new value is therefore locked out by default rather than by
 * someone remembering to add it to a deny-list - which is what makes {@link #SUSPENDED} take
 * effect on the very next request without touching the security classes at all.
 */
public enum AccountStatus {

    /** Registered, email not yet verified. */
    PENDING,

    ACTIVE,

    /** Indefinite, lifted only by an admin. */
    BANNED,

    /** Time-boxed. Lifted automatically by {@code SuspensionExpiryJob} when it expires. */
    SUSPENDED,

    /** Soft-deleted; PII is scrubbed later by {@code AccountAnonymizationJob}. */
    DELETED
}
