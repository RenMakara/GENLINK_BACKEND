package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    Optional<OtpCode> findTopByUserAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
        User user, OtpPurpose purpose);
    
    void deleteByUserAndPurposeAndVerifiedAtIsNull(User user, OtpPurpose purpose);
}
