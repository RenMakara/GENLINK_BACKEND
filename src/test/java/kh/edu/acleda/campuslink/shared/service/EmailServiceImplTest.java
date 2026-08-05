package kh.edu.acleda.campuslink.shared.service;

import jakarta.mail.internet.MimeMessage;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private org.springframework.mail.javamail.JavaMailSender mailSender;
    @Mock
    private MimeMessage mimeMessage;

    private EmailServiceImpl service(boolean mailEnabled) {
        return new EmailServiceImpl(mailSender, "no-reply@genlinkkh.com", mailEnabled);
    }

    @Test
    void skipsSmtpEntirelyWhenMailIsDisabled() {
        // Dev default: the OTP is logged and no connection is attempted, so local
        // work needs no SMTP credentials.
        service(false).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.FORGOT_PASSWORD);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendsThroughSmtpWhenMailIsEnabled() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void swallowsSendFailureSoRegistrationIsNotBlocked() {
        // A dead SMTP host must not break the calling flow: the OTP is already
        // persisted and logged, so the user can still complete verification.
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("535 Authentication failed"))
            .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER))
            .doesNotThrowAnyException();
    }

    @Test
    void disabledModeDoesNotEvenBuildAMessage() {
        service(false).sendOtpEmail("someone@example.com", "123456",
            Language.KHMER, OtpPurpose.REGISTER);

        verify(mailSender, never()).createMimeMessage();
    }
}
