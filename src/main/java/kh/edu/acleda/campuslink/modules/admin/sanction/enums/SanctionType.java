package kh.edu.acleda.campuslink.modules.admin.sanction.enums;

/** The weight of an enforcement action. */
public enum SanctionType {

    /** Recorded and shown to the user. Does not change {@code account_status}. */
    WARNING,

    /** Time-boxed. Sets {@code account_status = SUSPENDED} until {@code expires_at}. */
    SUSPENSION,

    /** Indefinite. Sets {@code account_status = BANNED} until lifted by an admin. */
    BAN
}
