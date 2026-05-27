package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpCode;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpType;
import kh.edu.acleda.campuslink.modules.auth.repository.OtpCodeRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

        otpService.issue(user, OtpType.REGISTER);

        verify(otpCodeRepository).deleteByUserAndType(user, OtpType.REGISTER);
        ArgumentCaptor<OtpCode> otpCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(otpCaptor.capture());
        OtpCode otpCode = otpCaptor.getValue();
        assertThat(otpCode.getUser()).isEqualTo(user);
        assertThat(otpCode.getCode()).matches("\\d{6}");
        assertThat(otpCode.getType()).isEqualTo(OtpType.REGISTER);
        assertThat(otpCode.isUsed()).isFalse();
        assertThat(otpCode.getExpiresAt()).isAfter(Instant.now());
        verify(otpDeliveryService).deliver(user, otpCode.getCode(), OtpType.REGISTER);
    }

    @Test
    void verifyMarksValidOtpAsUsed() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setCode("123456");
        otpCode.setType(OtpType.REGISTER);
        otpCode.setExpiresAt(Instant.now().plusSeconds(60));
        when(otpCodeRepository.findTopByUserAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
            user, "123456", OtpType.REGISTER)).thenReturn(Optional.of(otpCode));

        otpService.verify(user, "123456", OtpType.REGISTER);

        assertThat(otpCode.isUsed()).isTrue();
        verify(otpCodeRepository).save(otpCode);
    }

    @Test
    void verifyRejectsExpiredOtp() {
        OtpServiceImpl otpService = new OtpServiceImpl(otpCodeRepository, List.of(otpDeliveryService));
        User user = user("student@example.com");
        OtpCode otpCode = new OtpCode();
        otpCode.setUser(user);
        otpCode.setCode("123456");
        otpCode.setType(OtpType.REGISTER);
        otpCode.setExpiresAt(Instant.now().minusSeconds(60));
        when(otpCodeRepository.findTopByUserAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
            user, "123456", OtpType.REGISTER)).thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verify(user, "123456", OtpType.REGISTER))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("expired");
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Student");
        return user;
    }
}
