package kh.edu.acleda.campuslink.modules.admin.rbac.repository;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /** The permission set for a role. */
    @Query("SELECT rp.permissionCode FROM RolePermission rp WHERE rp.adminRole.id = :roleId")
    List<String> findPermissionCodesByRoleId(UUID roleId);

    List<RolePermission> findByAdminRoleId(UUID roleId);

    /** Clears a role's grants so the whole set can be rewritten. */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.adminRole.id = :roleId")
    void deleteByAdminRoleId(UUID roleId);
}
