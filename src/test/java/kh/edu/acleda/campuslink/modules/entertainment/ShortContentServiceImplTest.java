package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.ShortContentCreateRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ShortContentResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContent;
import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContentLike;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentLikeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.ShortContentRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.ShortContentServiceImpl;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortContentServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CID = UUID.randomUUID();

    @Mock private ShortContentRepository shortContentRepository;
    @Mock private ShortContentLikeRepository likeRepository;
    @Mock private SavedItemRepository savedItemRepository;
    @Mock private MediaService mediaService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private ShortContentServiceImpl service;
    private User user;
    private ShortContent content;

    @BeforeEach
    void setUp() {
        service = new ShortContentServiceImpl(shortContentRepository, likeRepository, savedItemRepository,
            mediaService, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setFullName("Ren Makara");
        user.setUsername("renmakara");
        user.setAvatarUrl("https://cdn.example.com/avatar.jpg");
        content = ShortContent.builder().titleEn("Study tips").category("EDUCATION").creator(user)
            .likeCount(0).saveCount(0).shareCount(0).build();
        ReflectionTestUtils.setField(content, "id", CID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(shortContentRepository.findByIdAndDeletedAtIsNull(CID)).thenReturn(Optional.of(content));
        when(shortContentRepository.save(any(ShortContent.class))).thenAnswer(i -> {
            ShortContent c = i.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", CID);
            }
            return c;
        });
        when(likeRepository.saveAndFlush(any(ShortContentLike.class))).thenAnswer(i -> i.getArgument(0));
        when(likeRepository.findByUserIdAndShortContentId(USER_ID, CID)).thenReturn(Optional.empty());
        when(likeRepository.findByUserIdAndShortContentIdIn(any(), any())).thenReturn(List.of());
        when(savedItemRepository.existsByUserIdAndItemTypeAndItemId(
            USER_ID, SavedItemType.SHORT_CONTENT, CID)).thenReturn(false);
        when(savedItemRepository.findSavedItemIds(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void createStartsWithZeroCountsAndTheCreatorSet() {
        ShortContentResponse r = service.create(
            new ShortContentCreateRequest("Study tips", "d", "EDUCATION", "v", "t", null, null), EN);

        assertThat(r.likeCount()).isZero();
        assertThat(r.creatorId()).isEqualTo(USER_ID);
        assertThat(r.creatorFullName()).isEqualTo("Ren Makara");
        assertThat(r.creatorUsername()).isEqualTo("renmakara");
        assertThat(r.creatorAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.jpg");
        assertThat(r.liked()).isFalse();
    }

    @Test
    void createResolvesOwnedConfirmedMediaUrls() {
        UUID videoMediaId = UUID.randomUUID();
        UUID thumbnailMediaId = UUID.randomUUID();
        when(mediaService.resolveConfirmedUrl(videoMediaId)).thenReturn("https://cdn.example.com/video.mp4");
        when(mediaService.resolveConfirmedUrl(thumbnailMediaId)).thenReturn("https://cdn.example.com/thumb.jpg");

        service.create(new ShortContentCreateRequest(
            "Study tips", "d", "EDUCATION", "untrusted-video", "untrusted-thumbnail",
            videoMediaId, thumbnailMediaId), EN);

        verify(mediaService).resolveConfirmedUrl(videoMediaId);
        verify(mediaService).resolveConfirmedUrl(thumbnailMediaId);
        ArgumentCaptor<ShortContent> captor = ArgumentCaptor.forClass(ShortContent.class);
        verify(shortContentRepository).save(captor.capture());
        assertThat(captor.getValue().getVideoUrl()).isEqualTo("https://cdn.example.com/video.mp4");
        assertThat(captor.getValue().getThumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
    }

    @Test
    void deleteSoftDeletesContentOwnedByCurrentUser() {
        service.delete(CID);

        assertThat(content.getDeletedAt()).isNotNull();
        verify(shortContentRepository).save(content);
    }

    @Test
    void deleteDoesNotRevealContentOwnedByAnotherUser() {
        User otherUser = new User();
        ReflectionTestUtils.setField(otherUser, "id", UUID.randomUUID());
        content.setCreator(otherUser);

        assertThatThrownBy(() -> service.delete(CID)).isInstanceOf(NotFoundException.class);

        verify(shortContentRepository, never()).save(content);
    }

    @Test
    void likeInsertsAndIncrementsWhenNotYetLiked() {
        ShortContentResponse r = service.like(CID, EN);

        verify(likeRepository).saveAndFlush(any(ShortContentLike.class));
        verify(shortContentRepository).incrementLike(CID);
        assertThat(r.liked()).isTrue();
    }

    @Test
    void likeIsIdempotent() {
        when(likeRepository.findByUserIdAndShortContentId(USER_ID, CID))
            .thenReturn(Optional.of(ShortContentLike.builder().user(user).shortContent(content).build()));

        service.like(CID, EN);

        verify(likeRepository, never()).saveAndFlush(any());
        verify(shortContentRepository, never()).incrementLike(any());
    }

    @Test
    void unlikeDeletesAndDecrements() {
        ShortContentLike like = ShortContentLike.builder().user(user).shortContent(content).build();
        when(likeRepository.findByUserIdAndShortContentId(USER_ID, CID)).thenReturn(Optional.of(like));

        ShortContentResponse r = service.unlike(CID, EN);

        verify(likeRepository).delete(like);
        verify(shortContentRepository).decrementLike(CID);
        assertThat(r.liked()).isFalse();
    }

    @Test
    void saveInsertsAndIncrements() {
        ShortContentResponse r = service.save(CID, EN);

        ArgumentCaptor<SavedItem> captor = ArgumentCaptor.forClass(SavedItem.class);
        verify(savedItemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getItemType()).isEqualTo(SavedItemType.SHORT_CONTENT);
        assertThat(captor.getValue().getItemId()).isEqualTo(CID);
        verify(shortContentRepository).incrementSave(CID);
        assertThat(r.saved()).isTrue();
    }

    @Test
    void savedContentReadsFromTheSharedProfileStore() {
        SavedItem savedItem = SavedItem.builder()
            .user(user)
            .itemType(SavedItemType.SHORT_CONTENT)
            .itemId(CID)
            .build();
        when(savedItemRepository.findByUserIdAndItemTypeOrderBySavedAtDesc(
            USER_ID, SavedItemType.SHORT_CONTENT)).thenReturn(List.of(savedItem));
        when(shortContentRepository.findAllById(List.of(CID))).thenReturn(List.of(content));
        when(savedItemRepository.findSavedItemIds(USER_ID, SavedItemType.SHORT_CONTENT, List.of(CID)))
            .thenReturn(List.of(CID));

        var saved = service.getSaved(EN);

        assertThat(saved).singleElement().satisfies(item -> {
            assertThat(item.shortContentId()).isEqualTo(CID);
            assertThat(item.saved()).isTrue();
        });
    }

    @Test
    void shareIncrementsTheShareCount() {
        service.share(CID, EN);

        verify(shortContentRepository).incrementShare(CID);
    }

    @Test
    void feedFlagsLikedAndSavedContent() {
        when(shortContentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(any()))
            .thenReturn(new PageImpl<>(List.of(content)));
        when(likeRepository.findByUserIdAndShortContentIdIn(any(), any()))
            .thenReturn(List.of(ShortContentLike.builder().user(user).shortContent(content).build()));

        var feed = service.getFeed(null, PageRequest.of(0, 20), EN);

        assertThat(feed.content()).singleElement().satisfies(c -> {
            assertThat(c.liked()).isTrue();
            assertThat(c.saved()).isFalse();
        });
    }

    @Test
    void unknownContentReadsAsNotFound() {
        when(shortContentRepository.findByIdAndDeletedAtIsNull(CID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(CID, EN)).isInstanceOf(NotFoundException.class);
    }
}
