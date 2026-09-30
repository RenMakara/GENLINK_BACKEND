package kh.edu.acleda.campuslink.modules.admin.auth;

import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminLoginRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMfaChallengeResponse;

public interface AdminAuthService {

    /** Password step of admin sign-in. */
    AdminMfaChallengeResponse login(AdminLoginRequest request);
}
