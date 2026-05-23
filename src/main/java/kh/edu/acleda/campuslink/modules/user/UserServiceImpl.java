package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse getUserProfile(UUID id) {
        log.debug("Fetching user profile for ID: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found with ID: {}", id);
                return new NotFoundException("User not found with id: " + id);
            });
        return toResponse(user);
    }

    @Override
    public User getUserByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email)
            .orElseThrow(() -> {
                log.warn("User not found with email: {}", email);
                return new NotFoundException("User not found with email: " + email);
            });
    }

    @Override
    public UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getAvatarUrl(),
            user.getAuthProvider(),
            user.getRole(),
            user.isActive()
        );
    }
}
