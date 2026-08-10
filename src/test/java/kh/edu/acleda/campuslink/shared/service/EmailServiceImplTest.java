package kh.edu.acleda.campuslink.shared.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.internet.MimeMessage;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ListAppender<ILoggingEvent> logs;
    private Logger emailLogger;

    @BeforeEach
    void captureEmailLogs() {
        emailLogger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
        emailLogger.setLevel(Level.INFO);
        logs = new ListAppender<>();
        logs.start();
        emailLogger.addAppender(logs);
    }

    @AfterEach
    void releaseEmailLogs() {
        if (emailLogger != null && logs != null) {
            emailLogger.detachAppender(logs);
        }
    }

    private boolean otpAppearsInLogs(String otp) {
        return logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(otp));
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

    /**
     * Security regression (testing-plan P1). The OTP is a short-lived auth secret; prod runs at
     * root=info and a failed deploy pipes {@code docker compose logs} into CI, so an INFO line
     * carrying the code would leak live OTPs into server logs and build history. With real mail
     * delivery on, the code must never be logged. Fails if the OTP log is moved back ahead of the
     * {@code mailEnabled} guard.
     */
    @Test
    void doesNotLogTheOtpWhenMailDeliveryIsEnabled() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service(true).sendOtpEmail("someone@example.com", "482913",
            Language.ENGLISH, OtpPurpose.REGISTER);

        assertThat(otpAppearsInLogs("482913"))
            .as("OTP must never appear in logs when real mail delivery is enabled")
            .isFalse();
        verify(mailSender).send(any(MimeMessage.class));
    }

    /**
     * The counterpart: in log-only mode (dev default) the log <em>is</em> the delivery channel,
     * so the code must still be printed - otherwise developers lose their only way to read it.
     * Together these two pin the behaviour on both sides of the {@code mailEnabled} switch.
     */
    @Test
    void stillLogsTheOtpInLogOnlyMode() {
        service(false).sendOtpEmail("someone@example.com", "482913",
            Language.ENGLISH, OtpPurpose.REGISTER);

        assertThat(otpAppearsInLogs("482913"))
            .as("log-only mode must keep printing the code; it is the dev delivery channel")
            .isTrue();
    }
}
