package kh.edu.acleda.campuslink.modules.user;

import kh.edu.acleda.campuslink.modules.user.dto.UserResponse;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import java.util.UUID;

public interface UserService {
    UserResponse getUserProfile(UUID id);
    User getUserByEmail(String email);
    UserResponse toResponse(User user);
}
