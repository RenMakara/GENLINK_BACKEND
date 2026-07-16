package kh.edu.acleda.campuslink.modules.profile;

import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.PublicProfileResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface ProfileService {
    ProfileResponse getCurrentProfile(String email);
    ProfileResponse updateCurrentProfile(String email, ProfileUpdateRequest request);
    ProfileResponse updateAvatar(String email, String avatarUrl);
    /** @param language used to localise the interest names on the profile. */
    PublicProfileResponse getPublicProfile(String username, Language language);
}
