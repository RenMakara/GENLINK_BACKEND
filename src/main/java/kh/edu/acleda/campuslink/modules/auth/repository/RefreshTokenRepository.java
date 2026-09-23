package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    interface LastActiveProjection {
        UUID getUserId();
        Instant getLastActiveAt();
    }

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Active sessions: not revoked and not yet expired. */
    List<RefreshToken> findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(User user, Instant now);

    /** Active sessions for the admin user detail view; token hashes are never mapped to its DTO. */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId "
        + "AND rt.revokedAt IS NULL AND rt.expiresAt > :now ORDER BY rt.createdAt DESC")
    Page<RefreshToken> findActiveByUserId(
        @Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    Optional<RefreshToken> findByIdAndUser(UUID id, User user);

    /** Returns one aggregate row per user for the admin directory's last-active column. */
    @Query("SELECT rt.user.id AS userId, MAX(rt.lastUsedAt) AS lastActiveAt "
        + "FROM RefreshToken rt WHERE rt.user.id IN :userIds GROUP BY rt.user.id")
    List<LastActiveProjection> findLastActiveByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    void deleteByUser(User user);
    
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt WHERE rt.user = :user AND rt.revokedAt IS NULL")
    void revokeByUser(@Param("user") User user, @Param("revokedAt") java.time.Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt WHERE rt.tokenHash = :tokenHash AND rt.revokedAt IS NULL")
    void revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") java.time.Instant revokedAt);
}
