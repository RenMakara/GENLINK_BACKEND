package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PermissionScopeResolverTest {

    private static final String EMAIL = "admin@example.com";
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock
    private AdminPermissionCache permissionCache;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moderatorIsScopedToOwnAuditRows() {
        authenticate();
        when(permissionCache.identityFor(EMAIL))
            .thenReturn(new AdminIdentity(ADMIN_ID, UUID.randomUUID(), "MODERATOR"));

        PermissionScopeResolver.AuditLogScope scope = new PermissionScopeResolver(permissionCache)
            .resolveAuditLogScope();

        assertThat(scope.visibility())
            .isEqualTo(PermissionScopeResolver.Visibility.OWN);
        assertThat(scope.adminUserId()).isEqualTo(ADMIN_ID);
        assertThat(scope.isSuperAdmin()).isFalse();
    }

    @Test
    void adminCanReadAllAuditRows() {
        authenticate();
        when(permissionCache.identityFor(EMAIL))
            .thenReturn(new AdminIdentity(ADMIN_ID, UUID.randomUUID(), "ADMIN"));

        PermissionScopeResolver.AuditLogScope scope = new PermissionScopeResolver(permissionCache)
            .resolveAuditLogScope();

        assertThat(scope.visibility())
            .isEqualTo(PermissionScopeResolver.Visibility.ALL);
    }

    @Test
    void unauthenticatedRequestsFailClosed() {
        PermissionScopeResolver.AuditLogScope scope = new PermissionScopeResolver(permissionCache)
            .resolveAuditLogScope();

        assertThat(scope.visibility())
            .isEqualTo(PermissionScopeResolver.Visibility.NONE);
        assertThat(scope.canReadAny()).isFalse();
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(EMAIL, null, java.util.List.of()));
    }
}
