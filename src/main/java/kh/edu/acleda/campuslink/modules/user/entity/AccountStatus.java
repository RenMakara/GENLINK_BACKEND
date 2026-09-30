package kh.edu.acleda.campuslink.modules.user.entity;

/** Lifecycle state of a user account. */
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
