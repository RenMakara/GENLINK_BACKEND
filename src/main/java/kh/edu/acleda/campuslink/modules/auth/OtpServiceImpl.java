package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.auth.repository.OtpCodeRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.HexFormat;
import java.util.Optional;

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
    public void issue(User user, OtpPurpose purpose) {
        otpCodeRepository.deleteByUserAndPurposeAndVerifiedAtIsNull(user, purpose);
        String code = generateCode();

        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setEmail(user.getEmail());
        otpCode.setOtpHash(hash(code));
        otpCode.setPurpose(purpose);
        otpCode.setExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        otpCode.setAttemptCount(0);
        otpCodeRepository.save(otpCode);

        deliveryServices.forEach(deliveryService -> deliveryService.deliver(user, code, purpose));
    }

    @Override
    @Transactional
    public void verify(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        OtpCode otpCode = findLatestPendingOtp(user, purpose, fallbackPurposes)
            .orElseThrow(() -> new ApiException("Invalid verification code"));

        if (otpCode.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Verification code has expired");
        }

        if (!otpCode.getOtpHash().equals(hash(code))) {
            otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);
            otpCodeRepository.save(otpCode);
            throw new ApiException("Invalid verification code");
        }

        otpCode.setVerifiedAt(Instant.now());
        otpCodeRepository.save(otpCode);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(OTP_BOUND));
    }

    private Optional<OtpCode> findLatestPendingOtp(User user, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        Optional<OtpCode> otpCode = otpCodeRepository
            .findTopByUserAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(user, purpose);
        if (otpCode.isPresent()) {
            return otpCode;
        }
        return Arrays.stream(fallbackPurposes)
            .map(fallbackPurpose -> otpCodeRepository
                .findTopByUserAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(user, fallbackPurpose))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
