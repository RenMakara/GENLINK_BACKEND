package kh.edu.acleda.campuslink.shared.service;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface EmailService {
    void sendOtpEmail(String to, String otp);

    void sendOtpEmail(String to, String otp, Language language, OtpPurpose purpose);
}
