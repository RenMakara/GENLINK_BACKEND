package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface OtpService {
    void issue(User user, OtpPurpose purpose);
    void validateOtp(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes);
    void verify(User user, String code, OtpPurpose purpose, OtpPurpose... fallbackPurposes);
}
