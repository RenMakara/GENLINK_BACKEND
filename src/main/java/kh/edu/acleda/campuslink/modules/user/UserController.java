package kh.edu.acleda.campuslink.modules.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.security.dto.request.DeleteAccountRequest;
import kh.edu.acleda.campuslink.modules.security.service.AccountSecurityService;
import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    /** Deletion revokes tokens and writes security audit rows, so it lives with the other credential-lifecycle operations. */
    private final AccountSecurityService accountSecurityService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("REST request to get current user profile: {}", userDetails.getUsername());
        UserResponse response = userService.toResponse(userService.getUserByEmail(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", response));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete my account",
        description = "Soft-deletes the caller's account and signs out every device. "
            + "Accounts with a password must confirm it in the request body; "
            + "social-only accounts may send no body.")
    public ResponseEntity<ApiResponse<Void>> deleteCurrentUser(
        @Valid @RequestBody(required = false) DeleteAccountRequest request) {
        accountSecurityService.deleteCurrentAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account deleted", null));
    }
}
