package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the row-level scope attached to a permission.
 *
 * <p>The permission matrix grants {@code audit:read} to MODERATOR and FINANCE_ADMIN with an
 * own-actions boundary. Keeping that boundary here prevents individual query services from
 * growing subtly different role checks. The resolver is deliberately fail-closed when there is
 * no authenticated staff identity.
 */
@Component
public class PermissionScopeResolver {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Set<String> OWN_AUDIT_ROLES = Set.of("MODERATOR", "FINANCE_ADMIN");

    private final AdminPermissionCache permissionCache;

    public PermissionScopeResolver(AdminPermissionCache permissionCache) {
        this.permissionCache = permissionCache;
    }

    /** Resolves the audit-log visibility for the current authenticated request. */
    public AuditLogScope resolveAuditLogScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getName() == null || authentication.getName().isBlank()) {
            return AuditLogScope.none();
        }

        AdminIdentity identity = permissionCache.identityFor(authentication.getName());
        if (identity == null || !identity.isAdmin()) {
            return AuditLogScope.none();
        }
        if (OWN_AUDIT_ROLES.contains(identity.roleCode())) {
            return AuditLogScope.own(identity.adminUserId(), identity.roleCode());
        }
        return AuditLogScope.all(identity.adminUserId(), identity.roleCode());
    }

    public record AuditLogScope(Visibility visibility, UUID adminUserId, String roleCode) {

        public static AuditLogScope none() {
            return new AuditLogScope(Visibility.NONE, null, null);
        }

        public static AuditLogScope own(UUID adminUserId, String roleCode) {
            return new AuditLogScope(Visibility.OWN, adminUserId, roleCode);
        }

        public static AuditLogScope all(UUID adminUserId, String roleCode) {
            return new AuditLogScope(Visibility.ALL, adminUserId, roleCode);
        }

        public boolean canReadAny() {
            return visibility != Visibility.NONE;
        }

        public boolean isSuperAdmin() {
            return SUPER_ADMIN.equals(roleCode);
        }
    }

    public enum Visibility {
        NONE,
        OWN,
        ALL
    }
}
