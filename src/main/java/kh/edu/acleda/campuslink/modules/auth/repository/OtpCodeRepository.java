package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    Optional<OtpCode> findTopByUserAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
        User user, OtpPurpose purpose);

    Optional<OtpCode> findTopByUserAndPurposeOrderByCreatedAtDesc(User user, OtpPurpose purpose);

    Optional<OtpCode> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    long countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, Instant createdAtAfter);
    
    void deleteByUserAndPurposeAndVerifiedAtIsNull(User user, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpCode otp SET otp.verifiedAt = :verifiedAt WHERE lower(otp.email) = lower(:email) AND otp.purpose = :purpose AND otp.verifiedAt IS NULL")
    void markActiveOtpsVerified(
        @Param("email") String email,
        @Param("purpose") OtpPurpose purpose,
        @Param("verifiedAt") Instant verifiedAt);
}
