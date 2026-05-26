package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    Optional<OtpCode> findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
        String email, String code, OtpType type);
    
    void deleteByEmailAndType(String email, OtpType type);
}
