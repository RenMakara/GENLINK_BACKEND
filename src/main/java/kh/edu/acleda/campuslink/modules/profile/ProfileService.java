package kh.edu.acleda.campuslink.modules.profile;

import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.PublicProfileResponse;

public interface ProfileService {
    ProfileResponse getCurrentProfile(String email);
    ProfileResponse updateCurrentProfile(String email, ProfileUpdateRequest request);
    ProfileResponse updateAvatar(String email, String avatarUrl);
    PublicProfileResponse getPublicProfile(String username);
}
