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

/**
 * The cached half of permission resolution.
 *
 * <p>Split into its own bean on purpose. Spring's {@code @Cacheable} works through a proxy, so
 * a cached method called from another method of the <em>same</em> class silently bypasses the
 * cache. Putting these two behind a separate bean means every call from
 * {@link AdminAuthorityProviderImpl} is a real proxied call - the alternative is caching that
 * looks correct, compiles, and never actually caches.
 *
 * <p><strong>Two caches, not one.</strong> Identity is keyed by email and only changes when
 * {@code admin_users} does; role authorities are keyed by role and only change when
 * {@code role_permissions} does. A single email-keyed cache would be simpler, but editing one
 * role's permissions would then have to evict everything - including the negative entries for
 * every ordinary user - sending the whole active user base back to the database at once.
 * Keeping them separate means a permission change never touches the mobile path.
 *
 * <p><strong>No TTL.</strong> The backend runs a single instance, so eviction-on-write is
 * exact and a time-based backstop buys nothing except a dependency. That makes eviction the
 * correctness mechanism: every write path that affects permissions must call one of the evict
 * methods below, and ADM-105 is the task that wires and tests them all.
 */
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

    /**
     * Is this account staff, and under which role?
     *
     * <p>This is the hot path: it runs for every authenticated request in the product, mobile
     * included. The overwhelmingly common answer is {@link AdminIdentity#NONE}, and that
     * answer is cached, so an ordinary user costs one query once and none thereafter.
     */
    @Cacheable(cacheNames = IDENTITY_CACHE, key = "#email", sync = true)
    public AdminIdentity identityFor(String email) {
        return adminUserRepository.findActiveByUserEmail(email)
            .map(admin -> new AdminIdentity(
                admin.getId(),
                admin.getAdminRole().getId(),
                admin.getAdminRole().getCode()))
            .orElse(AdminIdentity.NONE);
    }

    /**
     * The authorities a role grants.
     *
     * <p>{@code SUPER_ADMIN} is resolved in code to every {@link Permission}, and has no
     * {@code role_permissions} rows at all. Seeding its grants as well would create a second
     * source of truth, and the two would drift the first time a permission was added.
     *
     * @param roleCode used only to recognise SUPER_ADMIN; the cache key is the role id.
     */
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
                // A stored code with no matching constant: the role was granted something
                // that no @PreAuthorize can enforce. Skip it rather than fail - one stale
                // grant must not lock every admin out of the system - but say so loudly,
                // because it means the seed and the enum have diverged.
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
