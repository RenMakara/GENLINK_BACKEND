package kh.edu.acleda.campuslink.shared.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MailDeliveryRegistry deliveryRegistry;

    private EmailServiceImpl service(boolean mailEnabled) {
        return new EmailServiceImpl(mailSender, "no-reply@genlinkkh.com", mailEnabled,
            meterRegistry, deliveryRegistry);
    }

    private ListAppender<ILoggingEvent> logs;
    private Logger emailLogger;

    @BeforeEach
    void captureEmailLogs() {
        deliveryRegistry = new MailDeliveryRegistry();
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
        assertThat(deliveryRegistry.snapshot().lastOutcome())
            .isEqualTo(MailDeliveryRegistry.Outcome.NEVER_RUN);
    }

    @Test
    void sendsThroughSmtpWhenMailIsEnabled() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER);

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(deliveryRegistry.snapshot().lastOutcome())
            .isEqualTo(MailDeliveryRegistry.Outcome.SUCCESS);
    }

    @Test
    void swallowsSendFailureSoRegistrationIsNotBlocked() {
        // A dead SMTP host must not break the calling flow: the OTP is already
        // persisted, and the failure is observable through the error log/metric.
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("535 Authentication failed"))
            .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER))
            .doesNotThrowAnyException();
        assertThat(deliveryRegistry.snapshot().lastOutcome())
            .isEqualTo(MailDeliveryRegistry.Outcome.FAILURE);
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

    /**
     * A successful send is recorded as a metric, so delivery is observable beyond a log line.
     * The swallowed-failure design means logs are the only other trace; a counter is queryable
     * and alertable.
     */
    @Test
    void successfulDeliveryIsRecordedAsAMetric() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER);

        assertThat(meterRegistry.get("email.otp.send")
            .tags("outcome", "success", "purpose", "REGISTER").counter().count())
            .as("a successful OTP send must increment the success counter")
            .isEqualTo(1.0);
    }

    /**
     * The one that matters: a send failure is swallowed so registration is not blocked, so the
     * failure counter is the durable, queryable record that delivery failed. Fails if the failure
     * branch stops recording the metric.
     */
    @Test
    void failedDeliveryIsRecordedAsAMetric() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("535 Authentication failed"))
            .when(mailSender).send(any(MimeMessage.class));

        service(true).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.FORGOT_PASSWORD);

        assertThat(meterRegistry.get("email.otp.send")
            .tags("outcome", "failure", "purpose", "FORGOT_PASSWORD").counter().count())
            .as("a swallowed send failure must still increment the failure counter")
            .isEqualTo(1.0);
        assertThat(meterRegistry.find("email.otp.send").tags("outcome", "success").counter())
            .as("a failed send must not be recorded as a success")
            .isNull();
    }

    /**
     * Regression guard: log-only/dev mode is not a real delivery, so it must not pollute the
     * delivery metric. Fails if the counter is ever moved outside the real-send path.
     */
    @Test
    void logOnlyModeRecordsNoDeliveryMetric() {
        service(false).sendOtpEmail("someone@example.com", "123456",
            Language.ENGLISH, OtpPurpose.REGISTER);

        assertThat(meterRegistry.find("email.otp.send").counter())
            .as("log-only mode performs no delivery, so it records no delivery metric")
            .isNull();
    }
}
