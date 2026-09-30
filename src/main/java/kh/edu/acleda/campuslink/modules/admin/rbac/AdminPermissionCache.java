package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/** The cached half of permission resolution. */
@Service
public class AdminPermissionCache {

    public static final String IDENTITY_CACHE = "adminIdentityByEmail";
    public static final String ROLE_AUTHORITIES_CACHE = "adminAuthoritiesByRole";

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionCache.class);

    private final AdminUserRepository adminUserRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public AdminPermissionCache(AdminUserRepository adminUserRepository,
                                RolePermissionRepository rolePermissionRepository) {
        this.adminUserRepository = adminUserRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /** Is this account staff, and under which role? */
    @Cacheable(cacheNames = IDENTITY_CACHE, key = "#email", sync = true)
    public AdminIdentity identityFor(String email) {
        return adminUserRepository.findActiveByUserEmail(email)
            .map(admin -> new AdminIdentity(
                admin.getId(),
                admin.getAdminRole().getId(),
                admin.getAdminRole().getCode()))
            .orElse(AdminIdentity.NONE);
    }

    /** The authorities a role grants. */
    @Cacheable(cacheNames = ROLE_AUTHORITIES_CACHE, key = "#roleId", sync = true)
    public List<GrantedAuthority> authoritiesForRole(UUID roleId, String roleCode) {
        if (SUPER_ADMIN.equals(roleCode)) {
            List<GrantedAuthority> all = new ArrayList<>(Permission.values().length);
            for (Permission permission : Permission.values()) {
                all.add(permission.asAuthority());
            }
            return List.copyOf(all);
        }

        List<String> codes = rolePermissionRepository.findPermissionCodesByRoleId(roleId);
        List<GrantedAuthority> authorities = new ArrayList<>(codes.size());
        for (String code : codes) {
            Permission.fromCode(code).ifPresentOrElse(
                permission -> authorities.add(permission.asAuthority()),
                // A stored code with no matching constant: the role was granted something that no @PreAuthorize can enforce.
                () -> log.error("Ignoring unknown permission code '{}' on role {} - "
                    + "role_permissions has drifted from the Permission enum", code, roleId));
        }
        return List.copyOf(authorities);
    }

    /** Call after any change to {@code admin_users}: promotion, role change, deactivation. */
    @CacheEvict(cacheNames = IDENTITY_CACHE, key = "#email")
    public void evictIdentity(String email) {
        log.debug("Evicted cached admin identity for {}", email);
    }

    /** Call after any change to a role's {@code role_permissions}. */
    @CacheEvict(cacheNames = ROLE_AUTHORITIES_CACHE, key = "#roleId")
    public void evictRoleAuthorities(UUID roleId) {
        log.debug("Evicted cached authorities for role {}", roleId);
    }

    /** Blunt instrument for tests and for a role deletion, where per-key eviction is awkward. */
    @CacheEvict(cacheNames = {IDENTITY_CACHE, ROLE_AUTHORITIES_CACHE}, allEntries = true)
    public void evictAll() {
        log.info("Evicted all cached admin permission data");
    }
}
