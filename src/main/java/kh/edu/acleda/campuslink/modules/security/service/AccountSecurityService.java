package kh.edu.acleda.campuslink.modules.security.service;

import kh.edu.acleda.campuslink.modules.security.dto.request.ChangePasswordRequest;
import kh.edu.acleda.campuslink.modules.security.dto.response.SecurityActivityResponse;
import kh.edu.acleda.campuslink.modules.security.dto.response.SessionResponse;

import java.util.List;
import java.util.UUID;

public interface AccountSecurityService {

    void changePassword(ChangePasswordRequest request);

    List<SessionResponse> getActiveSessions();

    void revokeSession(UUID sessionId);

    void revokeAllSessions();

    List<SecurityActivityResponse> getRecentActivity();
}
