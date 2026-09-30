package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.List;
import kh.edu.acleda.campuslink.shared.security.AdminAuthorityProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/** Resolves a staff account's {@code PERM_*} authorities. */
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
