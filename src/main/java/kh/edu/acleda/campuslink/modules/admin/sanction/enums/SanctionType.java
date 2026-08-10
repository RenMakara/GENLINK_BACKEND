package kh.edu.acleda.campuslink.modules.admin.sanction.enums;

/**
 * The weight of an enforcement action.
 *
 * <p>All three live in one {@code user_sanctions} table because they are the same event at
 * different weights: an admin acted, for a reason, optionally time-boxed, optionally lifted.
 * The moderator's question is "what has this user done and what have we done about it?", which
 * is one timeline, not three.
 *
 * <p>Stored as a constraint-free VARCHAR, so a fourth value later needs no migration.
 */
public enum SanctionType {

    /** Recorded and shown to the user. Does not change {@code account_status}. */
    WARNING,

    /** Time-boxed. Sets {@code account_status = SUSPENDED} until {@code expires_at}. */
    SUSPENSION,

    /** Indefinite. Sets {@code account_status = BANNED} until lifted by an admin. */
    BAN
}
