package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.List;
import kh.edu.acleda.campuslink.shared.security.AdminAuthorityProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Resolves a staff account's {@code PERM_*} authorities.
 *
 * <p>Two cached lookups, in order: is this account staff, and what does its role grant. The
 * first returns {@link AdminIdentity#NONE} for everyone who is not, which is the answer for
 * effectively every request the product serves - so the mobile path costs one cache hit and
 * stops.
 *
 * <p>Resolution happens on every request rather than being baked into the JWT. That is what
 * makes a permission change take effect immediately: revoke a grant, evict, and the very next
 * request is refused. Claims in a token would stay valid until it expired.
 */
@Service
public class AdminAuthorityProviderImpl implements AdminAuthorityProvider {

    private final AdminPermissionCache cache;

    public AdminAuthorityProviderImpl(AdminPermissionCache cache) {
        this.cache = cache;
    }

    @Override
    public List<GrantedAuthority> authoritiesFor(String email) {
        AdminIdentity identity = cache.identityFor(email);
        if (!identity.isAdmin()) {
            return List.of();
        }
        return cache.authoritiesForRole(identity.roleId(), identity.roleCode());
    }
}
