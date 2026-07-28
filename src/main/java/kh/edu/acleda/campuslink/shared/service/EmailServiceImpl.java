package kh.edu.acleda.campuslink.shared.service;

import jakarta.mail.internet.MimeMessage;
import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final boolean mailEnabled;

    public EmailServiceImpl(JavaMailSender mailSender,
                            // The From is a verified sender address, deliberately separate from the SMTP
                            // login. Some providers (e.g. Brevo) use a login that is not a valid From
                            // header, so reusing spring.mail.username as the From made setFrom() throw
                            // AddressException before anything was ever sent. Falls back to the login (and
                            // then a safe default) so a Gmail setup, where From must equal the account,
                            // keeps working without extra config.
                            @Value("${app.mail.from:${spring.mail.username:no-reply@campuslinkkh.edu.kh}}") String fromEmail,
                            // Log-only mode for development: when false the OTP is logged and no SMTP
                            // connection is attempted. Lets dev run with no mail server at all.
                            @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.mailEnabled = mailEnabled;
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        sendOtpEmail(to, otp, Language.ENGLISH, OtpPurpose.REGISTER);
    }

    @Override
    public void sendOtpEmail(String to, String otp, Language language, OtpPurpose purpose) {
        log.info("**************************************************");
        log.info("EMAIL OTP FOR {}: {}", to, otp);
        log.info("**************************************************");

        // Development log-only mode: skip SMTP entirely and rely on the OTP logged above.
        // No mail server, no verified sender, and no address-parsing crash to wade through.
        if (!mailEnabled) {
            log.info("Mail sending disabled (app.mail.enabled=false) - not sending to {}. Use the OTP logged above.", to);
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
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, rootCauseMessage(e), e);
            // We don't throw exception here to allow user to see OTP in console during dev
        }
    }

    /**
     * Spring wraps the SMTP failure, so getMessage() on the top-level exception is
     * often just "Authentication failed". The provider's actual reply (for example
     * Gmail's "535-5.7.8 Username and Password not accepted") is only on the cause.
     */
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
