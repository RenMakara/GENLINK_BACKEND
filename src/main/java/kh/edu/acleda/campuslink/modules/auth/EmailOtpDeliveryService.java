package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.service.EmailService;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpDeliveryService implements OtpDeliveryService {

    private final EmailService emailService;

    public EmailOtpDeliveryService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public void deliver(User user, String code, OtpPurpose purpose) {
        emailService.sendOtpEmail(user.getEmail(), code, user.getPreferredLanguage(), purpose);
    }
}
