package kh.edu.acleda.campuslink.modules.bff.service;

import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;
import kh.edu.acleda.campuslink.modules.profile.ProfileService;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileProfileBffServiceImplTest {

    @Test
    void getProfileSummaryMapsOnlyMobileSafeFields() {
        ProfileService profileService = mock(ProfileService.class);
        MobileProfileBffServiceImpl bffService = new MobileProfileBffServiceImpl(profileService);
        UUID userId = UUID.randomUUID();
        when(profileService.getCurrentProfile("student@example.com")).thenReturn(new ProfileResponse(
            userId,
            "Student",
            "student",
            "student@example.com",
            "+85512345678",
            "https://cdn.example.com/avatar.png",
            "Flutter learner",
            null,
            null,
            "ACLEDA University",
            WorkStatus.STUDENT,
            "Phnom Penh",
            true,
            AccountStatus.ACTIVE,
            1240,
            4,
            Language.KHMER
        ));

        MobileProfileSummaryResponse response = bffService.getProfileSummary("student@example.com");

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("student");
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.totalXp()).isEqualTo(1240);
        assertThat(response.level()).isEqualTo(4);
        assertThat(response.preferredLanguage()).isEqualTo(Language.KHMER);
    }
}
