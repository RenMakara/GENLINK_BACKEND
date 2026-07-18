package kh.edu.acleda.campuslink.modules.community;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.CommunityServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private CommunityServiceImpl service;
    private User user;
    private Community community;

    @BeforeEach
    void setUp() {
        service = new CommunityServiceImpl(communityRepository, memberRepository, userRepository, currentUserProvider);

        user = new User();
        user.setEmail("user@example.com");
        user.setFullName("User");
        user.setUsername("user");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        community = Community.builder().name("Flutter KH").slug("flutter-kh")
            .privacy(CommunityPrivacy.PUBLIC).memberCount(3).postCount(0).build();
        ReflectionTestUtils.setField(community, "id", COMMUNITY_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(communityRepository.findByIdAndDeletedAtIsNull(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(communityRepository.existsBySlug(anyString())).thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(i -> {
            Community c = i.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", COMMUNITY_ID);
            }
            return c;
        });
    }

    /** Otherwise a community starts with an owner who is not in it. */
    @Test
    void creatorBecomesOwnerAndFirstMember() {
        CommunityResponse response = service.create(new CommunityCreateRequest(
            "Flutter KH", "Flutter devs", "TECH", null, null, CommunityPrivacy.PUBLIC));

        ArgumentCaptor<CommunityMember> captor = ArgumentCaptor.forClass(CommunityMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(CommunityRole.OWNER);
        assertThat(response.myRole()).isEqualTo(CommunityRole.OWNER);
        assertThat(response.joined()).isTrue();
        verify(communityRepository).adjustMemberCount(COMMUNITY_ID, 1);
    }

    @Test
    void slugIsDerivedFromTheName() {
        service.create(new CommunityCreateRequest("Flutter KH Devs", null, null, null, null, null));

        ArgumentCaptor<Community> captor = ArgumentCaptor.forClass(Community.class);
        verify(communityRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("flutter-kh-devs");
    }

    /** Khmer normalises away to nothing, which would otherwise yield an empty slug. */
    @Test
    void khmerOnlyNameFallsBackToAGeneratedSlug() {
        service.create(new CommunityCreateRequest("បច្ចេកវិទ្យា", null, null, null, null, null));

        ArgumentCaptor<Community> captor = ArgumentCaptor.forClass(Community.class);
        verify(communityRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isNotBlank().isEqualTo("community");
    }

    @Test
    void slugCollisionGetsANumericSuffix() {
        when(communityRepository.existsBySlug("flutter-kh")).thenReturn(true);
        when(communityRepository.existsBySlug("flutter-kh-2")).thenReturn(false);

        service.create(new CommunityCreateRequest("Flutter KH", null, null, null, null, null));

        ArgumentCaptor<Community> captor = ArgumentCaptor.forClass(Community.class);
        verify(communityRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("flutter-kh-2");
    }

    /** Join backs a button and (community_id, user_id) is unique. */
    @Test
    void joiningTwiceDoesNotDoubleCount() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.MEMBER).status(MemberStatus.ACTIVE).build()));

        service.join(COMMUNITY_ID);

        verify(memberRepository, never()).save(any(CommunityMember.class));
        verify(communityRepository, never()).adjustMemberCount(any(), anyInt());
    }

    /** The unique constraint forbids a second row, so rejoining reuses the old one. */
    @Test
    void rejoiningAfterLeavingReusesTheMembershipRow() {
        CommunityMember left = CommunityMember.builder()
            .role(CommunityRole.MEMBER).status(MemberStatus.LEFT).build();
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(Optional.of(left));

        service.join(COMMUNITY_ID);

        assertThat(left.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(communityRepository).adjustMemberCount(COMMUNITY_ID, 1);
    }

    @Test
    void joiningDecrementsNothingAndIncrementsTheCount() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.join(COMMUNITY_ID).myRole()).isEqualTo(CommunityRole.MEMBER);
        verify(communityRepository).adjustMemberCount(COMMUNITY_ID, 1);
    }

    /** Otherwise the community is orphaned with nobody able to administer it. */
    @Test
    void ownerCannotLeave() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.OWNER).status(MemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.leave(COMMUNITY_ID))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Transfer ownership");
    }

    @Test
    void leavingMarksTheMembershipLeftAndDecrementsTheCount() {
        CommunityMember member = CommunityMember.builder()
            .role(CommunityRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(Optional.of(member));

        service.leave(COMMUNITY_ID);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.LEFT);
        verify(communityRepository).adjustMemberCount(COMMUNITY_ID, -1);
    }

    @Test
    void updatingAsANonMemberReadsAsNotFound() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(COMMUNITY_ID,
            new CommunityCreateRequest("Hijacked", null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    /** The slug is a public URL: silently changing it breaks every shared link. */
    @Test
    void updateDoesNotRegenerateTheSlug() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.OWNER).status(MemberStatus.ACTIVE).build()));

        service.update(COMMUNITY_ID, new CommunityCreateRequest("Totally New Name", null, null, null, null, null));

        assertThat(community.getSlug()).isEqualTo("flutter-kh");
        assertThat(community.getName()).isEqualTo("Totally New Name");
    }

    @Test
    void onlyTheOwnerCanDelete() {
        when(memberRepository.findByCommunityIdAndUserId(COMMUNITY_ID, USER_ID))
            .thenReturn(Optional.of(CommunityMember.builder()
                .role(CommunityRole.ADMIN).status(MemberStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.delete(COMMUNITY_ID)).isInstanceOf(NotFoundException.class);
    }
}
