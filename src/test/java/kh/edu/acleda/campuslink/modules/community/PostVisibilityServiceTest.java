package kh.edu.acleda.campuslink.modules.community;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.service.PostVisibilityService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostVisibilityServiceTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID VIEWER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock private FollowRepository followRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;

    private PostVisibilityService visibility;
    private User author;

    @BeforeEach
    void setUp() {
        visibility = new PostVisibilityService(followRepository, communityMemberRepository);
        author = new User();
        ReflectionTestUtils.setField(author, "id", AUTHOR_ID);
    }

    @Test
    void publicPostIsVisibleToAnotherViewer() {
        assertThat(visibility.canView(post(Visibility.PUBLIC), VIEWER_ID)).isTrue();
    }

    @Test
    void onlyMePostIsVisibleToItsOwnerButHiddenFromOtherViewers() {
        Post post = post(Visibility.ONLY_ME);

        assertThat(visibility.canView(post, AUTHOR_ID)).isTrue();
        assertThat(visibility.canView(post, VIEWER_ID)).isFalse();
        assertThatThrownBy(() -> visibility.requireCanView(post, VIEWER_ID))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void friendsPostRequiresBothAcceptedFollowDirections() {
        Post post = post(Visibility.FRIENDS);
        when(followRepository.countMutualAcceptedFollows(VIEWER_ID, AUTHOR_ID)).thenReturn(1L, 2L);

        assertThat(visibility.canView(post, VIEWER_ID)).isFalse();
        assertThat(visibility.canView(post, VIEWER_ID)).isTrue();
    }

    @Test
    void communityPostRequiresAnActiveMembership() {
        Community community = Community.builder().name("GenLink").slug("genlink").build();
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);
        Post post = Post.builder().user(author).community(community).visibility(Visibility.COMMUNITY).build();
        when(communityMemberRepository.existsByCommunityIdAndUserIdAndStatus(
            COMMUNITY_ID, VIEWER_ID, MemberStatus.ACTIVE)).thenReturn(false, true);

        assertThat(visibility.canView(post, VIEWER_ID)).isFalse();
        assertThat(visibility.canView(post, VIEWER_ID)).isTrue();
    }

    private Post post(Visibility audience) {
        return Post.builder().user(author).visibility(audience).build();
    }
}
