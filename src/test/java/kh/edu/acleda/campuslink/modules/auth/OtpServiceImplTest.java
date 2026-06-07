package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.auth.repository.OtpCodeRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private OtpCodeRepository otpCodeRepository;
    @Mock
    private OtpDeliveryService otpDeliveryService;

    @Test
    void issueReplacesExistingOtpAndDeliversNewSixDigitCode() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        when(otpCodeRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(user.getEmail(), OtpPurpose.REGISTER))
            .thenReturn(Optional.empty());

        otpService.issue(user, OtpPurpose.REGISTER);

        verify(otpCodeRepository).markActiveOtpsVerified(
            org.mockito.ArgumentMatchers.eq(user.getEmail()),
            org.mockito.ArgumentMatchers.eq(OtpPurpose.REGISTER),
            org.mockito.ArgumentMatchers.any(Instant.class));
        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(otpCaptor.capture());
        OtpCode otpCode = otpCaptor.getValue();
        assertThat(otpCode.getUser()).isEqualTo(user);
        assertThat(otpCode.getEmail()).isEqualTo(user.getEmail());
        assertThat(otpCode.getOtpHash()).matches("[a-f0-9]{64}");
        assertThat(otpCode.getPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(otpCode.getVerifiedAt()).isNull();
        assertThat(otpCode.getAttemptCount()).isZero();
        assertThat(otpCode.getExpiresAt()).isAfter(Instant.now());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpDeliveryService).deliver(
            org.mockito.ArgumentMatchers.eq(user),
            codeCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(OtpPurpose.REGISTER));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(otpCode.getOtpHash()).isEqualTo(hash(codeCaptor.getValue()));
    }

    @Test
    void verifyMarksValidOtpAsUsed() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setOtpHash(hash("123456"));
        otpCode.setPurpose(OtpPurpose.REGISTER);
        otpCode.setExpiresAt(Instant.now().plusSeconds(60));
        when(otpCodeRepository.findTopByUserAndPurposeOrderByCreatedAtDesc(
            user, OtpPurpose.REGISTER)).thenReturn(Optional.of(otpCode));

        otpService.verify(user, "123456", OtpPurpose.REGISTER);

        assertThat(otpCode.getVerifiedAt()).isNotNull();
        verify(otpCodeRepository).save(otpCode);
    }

    @Test
    void verifyRejectsExpiredOtp() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setOtpHash(hash("123456"));
        otpCode.setPurpose(OtpPurpose.REGISTER);
        otpCode.setExpiresAt(Instant.now().minusSeconds(60));
        when(otpCodeRepository.findTopByUserAndPurposeOrderByCreatedAtDesc(
            user, OtpPurpose.REGISTER)).thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verify(user, "123456", OtpPurpose.REGISTER))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void verifyRejectsAfterTooManyAttempts() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setOtpHash(hash("123456"));
        otpCode.setPurpose(OtpPurpose.REGISTER);
        otpCode.setExpiresAt(Instant.now().plusSeconds(60));
        otpCode.setAttemptCount(5);
        when(otpCodeRepository.findTopByUserAndPurposeOrderByCreatedAtDesc(
            user, OtpPurpose.REGISTER)).thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verify(user, "123456", OtpPurpose.REGISTER))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(AuthErrorCode.OTP_TOO_MANY_ATTEMPTS);
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Student");
        return user;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
