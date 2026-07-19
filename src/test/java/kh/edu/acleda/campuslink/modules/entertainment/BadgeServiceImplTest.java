package kh.edu.acleda.campuslink.modules.entertainment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.BadgeServiceImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BadgeServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private BadgeServiceImpl service;
    private User user;
    private Badge earnedBadge;
    private Badge lockedBadge;

    @BeforeEach
    void setUp() {
        service = new BadgeServiceImpl(badgeRepository, userBadgeRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        earnedBadge = badge("Quiz Champion");
        lockedBadge = badge("Event Explorer");

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    private Badge badge(String name) {
        Badge b = Badge.builder().nameEn(name).descriptionEn("d").build();
        ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
        return b;
    }

    @Test
    void listFlagsWhichBadgesTheUserHasEarned() {
        when(badgeRepository.findByDeletedAtIsNullOrderByCreatedAtAsc())
            .thenReturn(List.of(earnedBadge, lockedBadge));
        when(userBadgeRepository.findByUserIdOrderByUnlockedAtDesc(USER_ID))
            .thenReturn(List.of(UserBadge.builder().user(user).badge(earnedBadge)
                .unlockedAt(LocalDateTime.now()).build()));

        List<BadgeResponse> all = service.getAll(EN);

        assertThat(all).hasSize(2);
        assertThat(all.get(0).earned()).isTrue();
        assertThat(all.get(0).unlockedAt()).isNotNull();
        assertThat(all.get(1).earned()).isFalse();
    }

    @Test
    void myBadgesReturnsOnlyUnlockedOnes() {
        when(userBadgeRepository.findByUserIdOrderByUnlockedAtDesc(USER_ID))
            .thenReturn(List.of(UserBadge.builder().user(user).badge(earnedBadge)
                .unlockedAt(LocalDateTime.now()).build()));

        assertThat(service.getMine(EN)).singleElement()
            .satisfies(b -> {
                assertThat(b.name()).isEqualTo("Quiz Champion");
                assertThat(b.earned()).isTrue();
            });
    }
}
