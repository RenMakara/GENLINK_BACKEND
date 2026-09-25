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

    /**
     * Resolves the admin profile during authentication.
     *
     * <p>Fetches the role eagerly for this query only: the caller immediately needs it to
     * resolve permissions, so a lazy proxy would cost a second query on every authenticated
     * admin request.
     */
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

    /**
     * Any admin profile for this user, <strong>including a soft-deleted one</strong>.
     *
     * <p>{@code admin_users.user_id} is UNIQUE unconditionally, so a soft-deleted profile still
     * occupies the slot. The bootstrap runner needs this rather than the deleted-aware lookup
     * above: checking only for a live profile would let it attempt an insert that the
     * constraint rejects, turning a clear refusal into a failed application start.
     */
    boolean existsByUserId(UUID userId);

    /**
     * The short-circuit on the authentication hot path.
     *
     * <p>Every authenticated request - overwhelmingly mobile, from users who are not staff -
     * asks this before doing any permission work. It must stay a cheap indexed existence
     * check, backed by {@code idx_admin_users_active}, and its result is cached.
     */
    boolean existsByUserIdAndActiveTrueAndDeletedAtIsNull(UUID userId);

    Page<AdminUser> findByDeletedAtIsNull(Pageable pageable);

    /** Batch-load staff roles for a page of users without an N+1 lazy-role query. */
    @EntityGraph(attributePaths = "adminRole")
    List<AdminUser> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Guards the "cannot demote the last SUPER_ADMIN" rule. Counting rather than listing
     * keeps the check cheap and makes the intent obvious at the call site.
     */
    long countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(String roleCode);
}
