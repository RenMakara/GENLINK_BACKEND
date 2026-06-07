package kh.edu.acleda.campuslink.shared.security;

import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }
        String email = authentication.getName();
        // TODO: Cache current user id per request if this becomes hot-path logic.
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"))
            .getId();
    }
}
