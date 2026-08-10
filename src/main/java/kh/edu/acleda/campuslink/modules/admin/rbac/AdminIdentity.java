package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.util.UUID;

/**
 * The minimum needed to decide what a staff account may do: which admin profile, and which
 * role.
 *
 * <p>A small immutable record rather than the {@code AdminUser} entity, because this value is
 * cached. Caching a JPA entity means caching something detached, lazily-proxied and mutable -
 * three ways to get a stale or broken object back out.
 */
public record AdminIdentity(UUID adminUserId, UUID roleId, String roleCode) {

    /**
     * The "not a staff account" answer, cached like any other.
     *
     * <p>A null object rather than {@code Optional} or null: negative results are the common
     * case by an enormous margin - every mobile request - and they have to be cacheable, or
     * every ordinary user pays a database query on every request forever.
     */
    public static final AdminIdentity NONE = new AdminIdentity(null, null, null);

    public boolean isAdmin() {
        return adminUserId != null;
    }
}
