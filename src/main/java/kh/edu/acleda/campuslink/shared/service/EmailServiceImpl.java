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

    /**
     * @param fromEmail   the From address, from {@code app.mail.from} — never from
     *                    {@code spring.mail.username}. With a transactional provider
     *                    (Brevo, SendGrid, Mailgun) the SMTP username is a generated
     *                    credential such as b31d5d001@smtp-brevo.com, not a mailbox
     *                    anyone owns, and the provider rejects any message whose From
     *                    is not one of its verified senders.
     * @param mailEnabled when false, the OTP is logged and no SMTP connection is
     *                    attempted. The dev profile defaults this off so local work
     *                    needs no SMTP credentials; it defaults on everywhere else.
     */
    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.from}") String fromEmail,
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
        if (!mailEnabled) {
            // Log-only mode (dev default): the log *is* the delivery channel, so the code is
            // printed here - and ONLY here. Returning also avoids an SMTP round trip that would
            // otherwise fail with the local machine's absent or mismatched credentials.
            //
            // The OTP is deliberately never logged once real delivery is enabled (staging/prod):
            // it is a short-lived auth secret, prod runs at root=info, and a failed deploy pipes
            // `docker compose logs` into CI output - an INFO line here would put live codes in the
            // server logs and in build history.
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
