package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;
import kh.edu.acleda.campuslink.modules.profile.ProfileService;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import org.springframework.stereotype.Service;

@Service
public class MobileProfileBffServiceImpl implements MobileProfileBffService {

    private final ProfileService profileService;

    public MobileProfileBffServiceImpl(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public MobileProfileSummaryResponse getProfileSummary(String email) {
        ProfileResponse profile = profileService.getCurrentProfile(email);
        return new MobileProfileSummaryResponse(
            profile.userId(),
            profile.fullName(),
            profile.username(),
            profile.email(),
            profile.phoneNumber(),
            profile.avatarUrl(),
            profile.bio(),
            profile.universityName(),
            profile.workStatus(),
            profile.location(),
            profile.emailVerified(),
            profile.accountStatus(),
            profile.totalXp(),
            profile.level(),
            profile.preferredLanguage()
        );
    }
}
