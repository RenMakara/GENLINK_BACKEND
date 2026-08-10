package kh.edu.acleda.campuslink.modules.admin.auth;

import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminLoginRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMfaChallengeResponse;

public interface AdminAuthService {

    /**
     * Password step of admin sign-in. Never returns a session - only a challenge that the TOTP
     * step exchanges for real tokens (SSOT §10.8).
     *
     * @throws kh.edu.acleda.campuslink.shared.exception.UnauthorizedException for every
     *     rejection, with an identical message regardless of cause.
     */
    AdminMfaChallengeResponse login(AdminLoginRequest request);
}
