package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.OtpPurpose;
import kh.edu.acleda.campuslink.modules.user.entity.User;

public interface OtpDeliveryService {
    void deliver(User user, String code, OtpPurpose purpose);
}
