package kh.edu.acleda.campuslink.modules.admin.rbac.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /**
     * The permission set for a role. Returns codes rather than entities: the resolver only
     * needs strings, and this keeps the cached value small.
     */
    @Query("SELECT rp.permissionCode FROM RolePermission rp WHERE rp.adminRole.id = :roleId")
    List<String> findPermissionCodesByRoleId(UUID roleId);

    List<RolePermission> findByAdminRoleId(UUID roleId);

    /**
     * Clears a role's grants so the whole set can be rewritten.
     *
     * <p>Updating permissions is a wholesale replace rather than an add/remove diff -
     * idempotent, and it avoids the class of bug where a partial diff leaves a grant behind.
     * The caller must evict the permission cache afterwards, or the change will not take
     * effect until the cache expires.
     */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.adminRole.id = :roleId")
    void deleteByAdminRoleId(UUID roleId);
}
