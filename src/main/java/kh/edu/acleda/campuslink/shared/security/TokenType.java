package kh.edu.acleda.campuslink.shared.security;

/**
 * Which API an access token is for.
 *
 * <p>Carried as the {@code typ} claim and enforced by {@link JwtAuthenticationFilter}: an
 * {@link #ADMIN} token is the only thing accepted on {@code /api/v1/admin/**}, and it is
 * rejected everywhere else.
 *
 * <p><strong>This is the single most important control in the admin design.</strong> Without
 * it, any user whose {@code users.role} happens to be {@code ADMIN} could reach the entire
 * admin surface from the mobile app, using the token the phone already holds. Role alone was
 * never meant to be the boundary between two different clients.
 */
public enum TokenType {

    /** The Flutter app. Default for tokens issued before this claim existed. */
    MOBILE,

    /** The admin portal. Shorter-lived, and required by every admin route. */
    ADMIN,

    /**
     * The 5-minute token handed out after the password step of admin login, before TOTP.
     *
     * <p><strong>It authenticates nothing.</strong> That is not enforced by a check anywhere -
     * it falls out of the design. {@link JwtAuthenticationFilter} compares this claim against
     * the type a route requires, and a route only ever requires {@link #MOBILE} or
     * {@link #ADMIN}. A token carrying this type therefore matches no route in the
     * application, and adding a new route cannot accidentally accept one.
     *
     * <p>That is why the challenge is a distinct type rather than a short-lived
     * {@link #ADMIN} token: the latter would be a fully privileged admin session that merely
     * expires quickly, which is exactly what the second factor exists to prevent.
     *
     * <p>Consumed only by {@code /admin/auth/mfa/verify}, which reads it directly instead of
     * going through the filter.
     */
    ADMIN_MFA_CHALLENGE;

    /** JWT claim name. Short, because it is on every request. */
    public static final String CLAIM = "typ";

    /**
     * Reads the claim off a parsed token.
     *
     * <p>An absent claim means {@link #MOBILE}: tokens issued before this change carry no
     * {@code typ}, and invalidating every live mobile session on deploy would be a needless
     * outage. Those tokens keep working on mobile routes and are refused on admin routes,
     * which is the outcome we want.
     *
     * <p>An unrecognised value also maps to {@code MOBILE} rather than failing closed. It can
     * only arise from a token this codebase issued with a type a later version introduced, and
     * the backend runs a single instance, so two versions never serve traffic at once. Mapping
     * it to the least-privileged type keeps the admin surface shut either way.
     */
    public static TokenType fromClaim(Object claim) {
        if (claim == null) {
            return MOBILE;
        }
        String value = claim.toString();
        for (TokenType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return MOBILE;
    }
}
