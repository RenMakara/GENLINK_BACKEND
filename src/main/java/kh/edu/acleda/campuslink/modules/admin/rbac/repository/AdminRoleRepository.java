package kh.edu.acleda.campuslink.modules.admin.rbac.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

    /** Roles are looked up by their stable code in seeds, bootstrap and tests. */
    Optional<AdminRole> findByCode(String code);

    boolean existsByCode(String code);

    /** Stable role ordering for the role list and permission editor. */
    List<AdminRole> findAllByOrderByCodeAsc();

    /** Counts active, non-deleted staff assigned to each role in one query. */
    @Query("SELECT a.adminRole.id AS roleId, COUNT(a) AS adminCount "
        + "FROM AdminUser a "
        + "WHERE a.active = true AND a.deletedAt IS NULL "
        + "GROUP BY a.adminRole.id")
    List<ActiveAdminCount> countActiveAdminsByRole();

    interface ActiveAdminCount {
        UUID getRoleId();

        Long getAdminCount();
    }
}
