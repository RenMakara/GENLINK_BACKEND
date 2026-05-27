package kh.edu.acleda.campuslink.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${spring.mail.username:no-reply@campuslinkkh.edu.kh}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendOtpEmail(String to, String otp) {
        log.info("**************************************************");
        log.info("EMAIL OTP FOR {}: {}", to, otp);
        log.info("**************************************************");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject("CampusLink KH - Email Verification");
            message.setText("Your verification code is: " + otp + "\n\nThis code will expire in 10 minutes.");
            
            mailSender.send(message);
            log.info("Successfully sent OTP email to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // We don't throw exception here to allow user to see OTP in console during dev
        }
    }
}
