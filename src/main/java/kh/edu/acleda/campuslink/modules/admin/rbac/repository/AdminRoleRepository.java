package kh.edu.acleda.campuslink.modules.admin.rbac.repository;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

    /** Roles are looked up by their stable code in seeds, bootstrap and tests. */
    Optional<AdminRole> findByCode(String code);

    boolean existsByCode(String code);
}
