package kh.edu.acleda.campuslink.shared.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final String OTP_REGISTER_EN_TEMPLATE_PATH = "templates/email/otp-register-en.html";
    private static final String OTP_REGISTER_KM_TEMPLATE_PATH = "templates/email/otp-register-km.html";
    private static final String FORGOT_PASSWORD_EN_TEMPLATE_PATH = "templates/email/forgot-password-en.html";
    private static final String FORGOT_PASSWORD_KM_TEMPLATE_PATH = "templates/email/forgot-password-km.html";

    /** Counter name for real OTP-email delivery outcomes, tagged {@code outcome=success|failure} and {@code purpose}. */
    private static final String DELIVERY_METRIC = "email.otp.send";

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final boolean mailEnabled;
    private final MeterRegistry meterRegistry;
    private final MailDeliveryRegistry deliveryRegistry;

    /** @param fromEmail the From address from {@code app.mail.from}. For standard SMTP */
    @Autowired
    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String fromEmail,
                            @Value("${app.mail.enabled:true}") boolean mailEnabled,
                            MeterRegistry meterRegistry,
                            MailDeliveryRegistry deliveryRegistry) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.mailEnabled = mailEnabled;
        this.meterRegistry = meterRegistry;
        this.deliveryRegistry = deliveryRegistry;
    }

    /** Compatibility seam for focused unit tests that do not need health instrumentation. */
    public EmailServiceImpl(JavaMailSender mailSender,
                            String fromEmail,
                            boolean mailEnabled,
                            MeterRegistry meterRegistry) {
        this(mailSender, fromEmail, mailEnabled, meterRegistry, new MailDeliveryRegistry());
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        sendOtpEmail(to, otp, Language.ENGLISH, OtpPurpose.REGISTER);
    }

    @Override
    public void sendOtpEmail(String to, String otp, Language language, OtpPurpose purpose) {
        if (!mailEnabled) {
            // Log-only mode (dev default): the log *is* the delivery channel, so the code is printed here - and ONLY here.
            log.info("**************************************************");
            log.info("EMAIL OTP FOR {}: {}", to, otp);
            log.info("**************************************************");
            log.info("Mail delivery is disabled (app.mail.enabled=false); OTP for {} logged only", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(resolveSubject(language, purpose));
            helper.setText(buildOtpHtml(otp, language, purpose), true);
            
            mailSender.send(message);
            log.info("Successfully sent OTP email to {}", to);
            meterRegistry.counter(DELIVERY_METRIC, "outcome", "success", "purpose", purpose.name()).increment();
            deliveryRegistry.recordSuccess();
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, rootCauseMessage(e), e);
            // The failure is swallowed by design (a dead SMTP host must not break registration).
            meterRegistry.counter(DELIVERY_METRIC, "outcome", "failure", "purpose", purpose.name()).increment();
            deliveryRegistry.recordFailure();
        }
    }

    /** Spring wraps the SMTP failure, so getMessage() on the top-level exception is often just "Authentication failed". */
    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.toString();
    }

    private String buildOtpHtml(String otp, Language language, OtpPurpose purpose) {
        String templatePath = resolveTemplatePath(language, purpose);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("OTP email template not found: " + templatePath);
            }
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return template.replace("{{otp}}", otp);
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load OTP email template", ex);
        }
    }

    private String resolveTemplatePath(Language language, OtpPurpose purpose) {
        boolean khmer = language == Language.KHMER;
        boolean forgotPassword = purpose == OtpPurpose.FORGOT_PASSWORD;
        if (forgotPassword) {
            return khmer ? FORGOT_PASSWORD_KM_TEMPLATE_PATH : FORGOT_PASSWORD_EN_TEMPLATE_PATH;
        }
        return khmer ? OTP_REGISTER_KM_TEMPLATE_PATH : OTP_REGISTER_EN_TEMPLATE_PATH;
    }

    private String resolveSubject(Language language, OtpPurpose purpose) {
        boolean forgotPassword = purpose == OtpPurpose.FORGOT_PASSWORD;
        if (language == Language.KHMER) {
            return forgotPassword
                ? "GenLink KH - កំណត់ពាក្យសម្ងាត់ឡើងវិញ"
                : "GenLink KH - ផ្ទៀងផ្ទាត់លេខកូដ";
        }
        return forgotPassword
            ? "GenLink KH - Password Reset Code"
            : "GenLink KH - Verification Code";
    }
}
