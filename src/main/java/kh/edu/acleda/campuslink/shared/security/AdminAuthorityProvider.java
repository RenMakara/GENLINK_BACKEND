package kh.edu.acleda.campuslink.shared.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/**
 * Supplies the {@code PERM_*} authorities for a staff account.
 *
 * <p>Declared here, implemented in {@code modules/admin/rbac}. The interface exists so that
 * {@link CustomUserDetailsService} - which every authenticated request goes through - does not
 * import the admin module. Admin is a leaf feature module; shared code depending on it directly
 * would invert the layering and make the module impossible to remove.
 *
 * <p>Permissions are resolved <strong>per request, from the database, through a cache</strong>
 * - never from a JWT claim. Baking them into the token would leave a revoked permission usable
 * until that token expired.
 */
public interface AdminAuthorityProvider {

    /**
     * @param email the account's login address.
     * @return the account's {@code PERM_*} authorities, or an empty list when the account is
     *     not staff - which is almost every caller, so implementations must make that case
     *     cheap.
     */
    List<GrantedAuthority> authoritiesFor(String email);
}
