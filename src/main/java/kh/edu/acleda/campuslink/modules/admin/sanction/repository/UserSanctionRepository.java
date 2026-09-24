package kh.edu.acleda.campuslink.modules.admin.sanction.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.entity.UserSanction;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserSanctionRepository extends JpaRepository<UserSanction, UUID> {

    /** A user's enforcement timeline: warnings, suspensions and bans interleaved. */
    Page<UserSanction> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Open sanction candidates for the detail view; expiry is checked in the service. */
    List<UserSanction> findByUserIdAndLiftedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * The open sanction of a given type, if any.
     *
     * <p>Backs both the idempotency guard (re-banning an already-banned user must not write a
     * second row) and lifting, which needs the row carrying {@code previousStatus}.
     */
    @Query("SELECT s FROM UserSanction s WHERE s.user.id = :userId AND s.type = :type "
        + "AND s.liftedAt IS NULL AND s.deletedAt IS NULL ORDER BY s.createdAt DESC LIMIT 1")
    Optional<UserSanction> findOpenByUserIdAndType(UUID userId, SanctionType type);

    /** Reloads an expiry candidate after its user row has been locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSanction s WHERE s.id = :id")
    Optional<UserSanction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Suspensions whose time is up, for the daily expiry job.
     *
     * <p>Matches {@code idx_user_sanctions_pending_expiry} - the partial index exists exactly
     * for this query, so the job never scans the full history table.
     */
    @Query("SELECT s FROM UserSanction s WHERE s.type = "
        + "kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType.SUSPENSION "
        + "AND s.liftedAt IS NULL AND s.expiresAt IS NOT NULL "
        + "AND s.expiresAt < :now AND s.deletedAt IS NULL")
    List<UserSanction> findExpired(Instant now);
}
