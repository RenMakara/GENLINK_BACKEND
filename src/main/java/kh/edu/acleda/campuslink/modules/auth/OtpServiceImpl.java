package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpType;
import kh.edu.acleda.campuslink.modules.auth.repository.OtpCodeRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OtpServiceImpl implements OtpService {

    private static final int OTP_BOUND = 1_000_000;
    private static final int OTP_TTL_MINUTES = 10;

    private final OtpCodeRepository otpCodeRepository;
    private final List<OtpDeliveryService> deliveryServices;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpServiceImpl(OtpCodeRepository otpCodeRepository,
                          List<OtpDeliveryService> deliveryServices) {
        this.otpCodeRepository = otpCodeRepository;
        this.deliveryServices = deliveryServices;
    }

    @Override
    @Transactional
    public void issue(User user, OtpType type) {
        otpCodeRepository.deleteByUserAndType(user, type);

        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setCode(generateCode());
        otpCode.setType(type);
        otpCode.setExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        otpCode.setUsed(false);
        otpCodeRepository.save(otpCode);

        deliveryServices.forEach(deliveryService -> deliveryService.deliver(user, otpCode.getCode(), type));
    }

    @Override
    @Transactional
    public void verify(User user, String code, OtpType type) {
        OtpCode otpCode = otpCodeRepository
            .findTopByUserAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(user, code, type)
            .orElseThrow(() -> new ApiException("Invalid verification code"));

        if (otpCode.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Verification code has expired");
        }

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(OTP_BOUND));
    }
}
