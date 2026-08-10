package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Resolution logic, with the cache bean called directly (no Spring proxy, so no caching).
 * Caching behaviour itself is covered by {@link AdminPermissionCacheTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthorityProviderImplTest {

    private static final String ADMIN_EMAIL = "mod@example.com";
    private static final String USER_EMAIL = "user@example.com";
    private static final UUID ROLE_ID = UUID.randomUUID();

    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private AdminAuthorityProviderImpl provider() {
        return new AdminAuthorityProviderImpl(
            new AdminPermissionCache(adminUserRepository, rolePermissionRepository));
    }

    @Test
    @DisplayName("a MODERATOR gets the permissions its role grants, and nothing else")
    void moderatorGetsItsRolesPermissions() {
        stubAdmin("MODERATOR");
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID))
            .thenReturn(List.of("user:ban", "user:suspend", "community:moderate"));

        List<String> authorities = names(provider().authoritiesFor(ADMIN_EMAIL));

        assertThat(authorities).contains("PERM_user:ban", "PERM_user:suspend");
        // The negative half matters as much: a moderator must not hold RBAC management.
        assertThat(authorities).doesNotContain("PERM_rbac:manage", "PERM_user:delete");
    }

    @Test
    @DisplayName("SUPER_ADMIN holds every permission with no role_permissions rows at all")
    void superAdminIsResolvedInCode() {
        stubAdmin("SUPER_ADMIN");

        List<GrantedAuthority> authorities = provider().authoritiesFor(ADMIN_EMAIL);

        assertThat(authorities).hasSize(Permission.values().length);
        assertThat(names(authorities)).contains("PERM_rbac:manage", "PERM_settings:manage");
        // The point of resolving it in code: the seed grants SUPER_ADMIN nothing, so a second
        // source of truth cannot drift from this one.
        verify(rolePermissionRepository, never()).findPermissionCodesByRoleId(any());
    }

    @Test
    @DisplayName("a non-staff account gets no permissions and costs no role lookup")
    void ordinaryUserGetsNothing() {
        when(adminUserRepository.findActiveByUserEmail(USER_EMAIL)).thenReturn(Optional.empty());

        assertThat(provider().authoritiesFor(USER_EMAIL)).isEmpty();

        // The mobile hot path stops at the identity lookup - it must never reach the role
        // query, cached or not.
        verify(rolePermissionRepository, never()).findPermissionCodesByRoleId(any());
    }

    @Test
    @DisplayName("a permission code with no matching constant is skipped, not fatal")
    void unknownPermissionCodeIsSkipped() {
        stubAdmin("MODERATOR");
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID))
            .thenReturn(List.of("user:ban", "user:obliterate"));

        List<String> authorities = names(provider().authoritiesFor(ADMIN_EMAIL));

        // One stale row in role_permissions must not lock every admin out of the system.
        assertThat(authorities).containsExactly("PERM_user:ban");
    }

    @Test
    @DisplayName("resolution reads the database, never a token claim")
    void resolvesFromTheDatabaseEveryTime() {
        stubAdmin("MODERATOR");
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID)).thenReturn(List.of("user:ban"));

        AdminAuthorityProviderImpl provider = provider();
        provider.authoritiesFor(ADMIN_EMAIL);
        provider.authoritiesFor(ADMIN_EMAIL);

        // Uncached (no proxy here), so both calls hit the repository. That is the property
        // that makes a revoked permission take effect on the next request rather than when
        // the token expires.
        verify(adminUserRepository, times(2)).findActiveByUserEmail(ADMIN_EMAIL);
    }

    private void stubAdmin(String roleCode) {
        AdminRole role = new AdminRole();
        ReflectionTestUtils.setField(role, "id", ROLE_ID);
        role.setCode(roleCode);
        role.setName(roleCode);

        AdminUser admin = new AdminUser();
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        admin.setAdminRole(role);
        admin.setActive(true);

        when(adminUserRepository.findActiveByUserEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
    }

    private List<String> names(List<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
