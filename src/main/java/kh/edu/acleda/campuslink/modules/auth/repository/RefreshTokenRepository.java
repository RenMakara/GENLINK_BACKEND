package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
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
