package kh.edu.acleda.campuslink.modules.security.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.security.dto.request.ChangePasswordRequest;
import kh.edu.acleda.campuslink.modules.security.dto.response.SecurityActivityResponse;
import kh.edu.acleda.campuslink.modules.security.dto.response.SessionResponse;
import kh.edu.acleda.campuslink.modules.security.service.AccountSecurityService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Account Security")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;

    public AccountSecurityController(AccountSecurityService accountSecurityService) {
        this.accountSecurityService = accountSecurityService;
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountSecurityService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed. Please sign in again.", null));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getActiveSessions() {
        return ResponseEntity.ok(ApiResponse.success("Active sessions retrieved", accountSecurityService.getActiveSessions()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable UUID sessionId) {
        accountSecurityService.revokeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Session revoked", null));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions() {
        accountSecurityService.revokeAllSessions();
        return ResponseEntity.ok(ApiResponse.success("All sessions revoked", null));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<SecurityActivityResponse>>> getRecentActivity() {
        return ResponseEntity.ok(ApiResponse.success("Security activity retrieved", accountSecurityService.getRecentActivity()));
    }
}
