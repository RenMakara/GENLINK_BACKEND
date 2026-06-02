package kh.edu.acleda.campuslink.modules.profile;

import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;

    public ProfileServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(String email) {
        return toResponse(loadVisibleUser(email));
    }

    @Override
    @Transactional
    public ProfileResponse updateCurrentProfile(String email, ProfileUpdateRequest request) {
        User user = loadEditableUser(email);

        if (request.username() != null && !request.username().equals(user.getUsername())
            && userRepository.existsByUsername(request.username())) {
            throw new ApiException("Username already registered");
        }
        if (request.phoneNumber() != null && !request.phoneNumber().equals(user.getPhoneNumber())
            && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new ApiException("Phone already registered");
        }

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().isBlank() ? null : request.phoneNumber());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.universityName() != null) {
            user.setUniversityName(request.universityName());
        }
        if (request.workStatus() != null) {
            user.setWorkStatus(request.workStatus());
        }
        if (request.location() != null) {
            user.setLocation(request.location());
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(String email, String avatarUrl) {
        User user = loadEditableUser(email);
        user.setAvatarUrl(avatarUrl);
        return toResponse(userRepository.save(user));
    }

    private User loadVisibleUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found with email: " + email));
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            throw new NotFoundException("User not found with email: " + email);
        }
        return user;
    }

    private User loadEditableUser(String email) {
        User user = loadVisibleUser(email);
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        return user;
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getDateOfBirth(),
            user.getGender(),
            user.getUniversityName(),
            user.getWorkStatus(),
            user.getLocation(),
            user.isEmailVerified(),
            user.getAccountStatus(),
            user.getTotalXp(),
            user.getLevel(),
            user.getPreferredLanguage()
        );
    }
}
