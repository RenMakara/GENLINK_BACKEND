package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("REST request to get current user profile: {}", userDetails.getUsername());
        UserResponse response = userService.toResponse(userService.getUserByEmail(userDetails.getUsername()));
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", response));
    }
}
