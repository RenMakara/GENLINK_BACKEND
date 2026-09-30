package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

/** Caching behaviour, which only exists through Spring's proxy. */
class AdminPermissionCacheTest {

    private static final String ADMIN_EMAIL = "mod@example.com";
    private static final String USER_EMAIL = "user@example.com";
    private static final UUID ROLE_ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private AdminUserRepository adminUserRepository;
    private RolePermissionRepository rolePermissionRepository;
    private AdminPermissionCache cache;

    @Configuration
    @EnableCaching
    static class CachingContext {
        @Bean
        CacheManager cacheManager() {
            // Same implementation Spring Boot picks with no cache provider on the classpath.
            return new ConcurrentMapCacheManager(
                AdminPermissionCache.IDENTITY_CACHE, AdminPermissionCache.ROLE_AUTHORITIES_CACHE);
        }
    }

    @BeforeEach
    void setUp() {
        adminUserRepository = mock(AdminUserRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(AdminUserRepository.class, () -> adminUserRepository);
        context.registerBean(RolePermissionRepository.class, () -> rolePermissionRepository);
        context.register(CachingContext.class);
        context.registerBean(AdminPermissionCache.class);
        context.refresh();

        cache = context.getBean(AdminPermissionCache.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    /** The hot-path assertion. */
    @Test
    @DisplayName("a non-staff account is negatively cached: one query, then none")
    void negativeResultIsCached() {
        when(adminUserRepository.findActiveByUserEmail(USER_EMAIL)).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThat(cache.identityFor(USER_EMAIL)).isEqualTo(AdminIdentity.NONE);
        }

        verify(adminUserRepository, times(1)).findActiveByUserEmail(USER_EMAIL);
    }

    @Test
    @DisplayName("a staff identity is cached")
    void identityIsCached() {
        stubAdmin();

        cache.identityFor(ADMIN_EMAIL);
        cache.identityFor(ADMIN_EMAIL);

        verify(adminUserRepository, times(1)).findActiveByUserEmail(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("role authorities are cached per role")
    void roleAuthoritiesAreCached() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID)).thenReturn(List.of("user:ban"));

        cache.authoritiesForRole(ROLE_ID, "MODERATOR");
        cache.authoritiesForRole(ROLE_ID, "MODERATOR");

        verify(rolePermissionRepository, times(1)).findPermissionCodesByRoleId(ROLE_ID);
    }

    /** Eviction is the correctness mechanism here - there is no TTL - so this is the test that matters most. */
    @Test
    @DisplayName("evicting a role's authorities makes the next lookup see the change")
    void evictionMakesChangesTakeEffect() {
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID))
            .thenReturn(List.of("user:ban", "user:delete"), List.of("user:ban"));

        assertThat(cache.authoritiesForRole(ROLE_ID, "MODERATOR")).hasSize(2);

        cache.evictRoleAuthorities(ROLE_ID);

        assertThat(cache.authoritiesForRole(ROLE_ID, "MODERATOR")).hasSize(1);
        verify(rolePermissionRepository, times(2)).findPermissionCodesByRoleId(ROLE_ID);
    }

    @Test
    @DisplayName("evicting an identity makes a revoked admin stop resolving as staff")
    void evictingIdentityPicksUpRevocation() {
        AdminUser admin = adminUser();
        when(adminUserRepository.findActiveByUserEmail(ADMIN_EMAIL))
            .thenReturn(Optional.of(admin), Optional.empty());

        assertThat(cache.identityFor(ADMIN_EMAIL).isAdmin()).isTrue();

        cache.evictIdentity(ADMIN_EMAIL);

        // Deactivation has to bite immediately; a revoked admin still resolving as staff is
        // exactly the failure eviction exists to prevent.
        assertThat(cache.identityFor(ADMIN_EMAIL).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("evictAll clears both caches")
    void evictAllClearsBoth() {
        stubAdmin();
        when(rolePermissionRepository.findPermissionCodesByRoleId(ROLE_ID)).thenReturn(List.of("user:ban"));
        cache.identityFor(ADMIN_EMAIL);
        cache.authoritiesForRole(ROLE_ID, "MODERATOR");

        cache.evictAll();

        cache.identityFor(ADMIN_EMAIL);
        cache.authoritiesForRole(ROLE_ID, "MODERATOR");
        verify(adminUserRepository, times(2)).findActiveByUserEmail(ADMIN_EMAIL);
        verify(rolePermissionRepository, times(2)).findPermissionCodesByRoleId(ROLE_ID);
    }

    private void stubAdmin() {
        when(adminUserRepository.findActiveByUserEmail(ADMIN_EMAIL)).thenReturn(Optional.of(adminUser()));
    }

    private AdminUser adminUser() {
        AdminRole role = new AdminRole();
        ReflectionTestUtils.setField(role, "id", ROLE_ID);
        role.setCode("MODERATOR");
        role.setName("Moderator");

        AdminUser admin = new AdminUser();
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        admin.setAdminRole(role);
        admin.setActive(true);
        return admin;
    }
}
