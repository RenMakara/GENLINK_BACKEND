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
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MAX_RESENDS_PER_WINDOW = 5;
    private static final long RESEND_WINDOW_MINUTES = 15;
    private static final long MIN_RESEND_WAIT_SECONDS = 60;

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
        enforceResendLimit(user, purpose);
        otpCodeRepository.markActiveOtpsVerified(user.getEmail(), purpose, Instant.now());
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
    public void validateOtp(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        validateCode(user, code, purpose, fallbackPurposes);
    }

    @Override
    @Transactional
    public void verify(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        OtpCode otpCode = validateCode(user, code, purpose, fallbackPurposes);
        otpCode.setVerifiedAt(Instant.now());
        otpCodeRepository.save(otpCode);
    }

    private OtpCode validateCode(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        OtpCode otpCode = findLatestOtp(user, purpose, fallbackPurposes)
            .orElseThrow(() -> new ApiException("Invalid verification code", AuthErrorCode.OTP_INVALID));

        if (otpCode.getVerifiedAt() != null) {
            throw new ApiException("Verification code has already been used", AuthErrorCode.OTP_ALREADY_USED);
        }

        if (otpCode.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Verification code has expired", AuthErrorCode.OTP_EXPIRED);
        }

        if (otpCode.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            throw new ApiException("Too many verification attempts. Please request a new code.", AuthErrorCode.OTP_TOO_MANY_ATTEMPTS);
        }

        if (!otpCode.getOtpHash().equals(hash(code))) {
            otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);
            otpCodeRepository.save(otpCode);
            if (otpCode.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
                throw new ApiException("Too many verification attempts. Please request a new code.", AuthErrorCode.OTP_TOO_MANY_ATTEMPTS);
            }
            throw new ApiException("Invalid verification code", AuthErrorCode.OTP_INVALID);
        }
        return otpCode;
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(OTP_BOUND));
    }

    private Optional<OtpCode> findLatestOtp(User user, OtpPurpose purpose, OtpPurpose... fallbackPurposes) {
        Optional<OtpCode> otpCode = otpCodeRepository
            .findTopByUserAndPurposeOrderByCreatedAtDesc(user, purpose);
        if (otpCode.isPresent()) {
            return otpCode;
        }
        return Arrays.stream(fallbackPurposes)
            .map(fallbackPurpose -> otpCodeRepository
                .findTopByUserAndPurposeOrderByCreatedAtDesc(user, fallbackPurpose))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    private void enforceResendLimit(User user, OtpPurpose purpose) {
        Instant now = Instant.now();
        Optional<OtpCode> latestOtp = otpCodeRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
            user.getEmail(), purpose);
        if (latestOtp.isPresent()
            && latestOtp.get().getCreatedAt() != null
            && latestOtp.get().getCreatedAt().plus(MIN_RESEND_WAIT_SECONDS, ChronoUnit.SECONDS).isAfter(now)) {
            throw new ApiException("Please wait before requesting another verification code", AuthErrorCode.OTP_RESEND_TOO_SOON);
        }

        long recentOtpCount = otpCodeRepository.countByEmailIgnoreCaseAndPurposeAndCreatedAtAfter(
            user.getEmail(),
            purpose,
            now.minus(RESEND_WINDOW_MINUTES, ChronoUnit.MINUTES));
        if (recentOtpCount >= MAX_RESENDS_PER_WINDOW) {
            throw new ApiException("OTP resend limit exceeded", AuthErrorCode.OTP_RESEND_LIMIT_EXCEEDED);
        }
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
