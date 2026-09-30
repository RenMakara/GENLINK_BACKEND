package kh.edu.acleda.campuslink.modules.admin.rbac.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /** Resolves the admin profile during authentication. */
    @Query("SELECT a FROM AdminUser a JOIN FETCH a.adminRole "
        + "WHERE a.user.email = :email AND a.active = true AND a.deletedAt IS NULL")
    Optional<AdminUser> findActiveByUserEmail(String email);

    /** Fetches the role in the same query for the admin user detail projection. */
    @EntityGraph(attributePaths = "adminRole")
    Optional<AdminUser> findByUserIdAndDeletedAtIsNull(UUID userId);

    /** Loads any profile, including a revoked one, for promotion/recovery decisions. */
    @EntityGraph(attributePaths = {"user", "adminRole"})
    Optional<AdminUser> findByUserId(UUID userId);

    /** Serialises one admin mutation against a concurrent role/deactivation request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminUser a JOIN FETCH a.user JOIN FETCH a.adminRole "
        + "WHERE a.id = :adminId")
    Optional<AdminUser> findByIdForUpdate(@Param("adminId") UUID adminId);

    /** The admin directory includes both active and revoked staff profiles. */
    @Override
    @EntityGraph(attributePaths = {"user", "adminRole"})
    Page<AdminUser> findAll(Pageable pageable);

    /** Any admin profile for this user, including a soft-deleted one . */
    boolean existsByUserId(UUID userId);

    /** The short-circuit on the authentication hot path. */
    boolean existsByUserIdAndActiveTrueAndDeletedAtIsNull(UUID userId);

    Page<AdminUser> findByDeletedAtIsNull(Pageable pageable);

    /** Batch-load staff roles for a page of users without an N+1 lazy-role query. */
    @EntityGraph(attributePaths = "adminRole")
    List<AdminUser> findByUserIdIn(Collection<UUID> userIds);

    /** Guards the "cannot demote the last SUPER_ADMIN" rule. */
    long countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(String roleCode);
}
