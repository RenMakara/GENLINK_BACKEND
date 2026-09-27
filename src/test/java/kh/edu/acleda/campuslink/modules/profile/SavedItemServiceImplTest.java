package kh.edu.acleda.campuslink.modules.profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SaveItemRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SavedItemResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.SavedItem;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreview;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemPreviewResolver;
import kh.edu.acleda.campuslink.modules.profile.service.impl.SavedItemServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavedItemServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    @Mock private SavedItemRepository savedItemRepository;
    @Mock private SavedItemPreviewResolver previewResolver;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private SavedItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SavedItemServiceImpl(savedItemRepository, previewResolver, userRepository, currentUserProvider);
        User user = new User();
        user.setEmail("user@example.com");
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(savedItemRepository.save(any(SavedItem.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Save backs a UI toggle, so a double tap must not hit the unique constraint. */
    @Test
    void savingAnAlreadySavedItemReturnsTheExistingRowWithoutInserting() {
        when(savedItemRepository.findByUserIdAndItemTypeAndItemId(USER_ID, SavedItemType.POST, POST_ID))
            .thenReturn(Optional.of(savedPost()));
        stubPreview(new SavedItemPreview("Hello GenLink", null, null));

        SavedItemResponse response = service.save(new SaveItemRequest(SavedItemType.POST, POST_ID), Language.ENGLISH);

        assertThat(response.itemId()).isEqualTo(POST_ID);
        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(savedItemRepository, never()).save(any(SavedItem.class));
    }

    @Test
    void newSaveUsesTheUserRowLockBeforeInserting() {
        when(savedItemRepository.findByUserIdAndItemTypeAndItemId(USER_ID, SavedItemType.POST, POST_ID))
            .thenReturn(Optional.empty());
        stubPreview(new SavedItemPreview("Hello GenLink", null, null));

        service.save(new SaveItemRequest(SavedItemType.POST, POST_ID), Language.ENGLISH);

        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(savedItemRepository).save(any(SavedItem.class));
    }

    @Test
    void savingAnUnauthorizedPostDoesNotCreateASavedItem() {
        when(previewResolver.resolve(SavedItemType.POST, List.of(POST_ID), Language.ENGLISH))
            .thenReturn(Map.of());

        assertThatThrownBy(() -> service.save(
            new SaveItemRequest(SavedItemType.POST, POST_ID), Language.ENGLISH))
            .isInstanceOf(NotFoundException.class);

        verify(userRepository, never()).findByIdForUpdate(USER_ID);
        verify(savedItemRepository, never()).save(any(SavedItem.class));
    }

    @Test
    void listReturnsHydratedRows() {
        when(savedItemRepository.findByUserIdOrderBySavedAtDesc(USER_ID)).thenReturn(List.of(savedPost()));
        stubPreview(new SavedItemPreview("Hello GenLink", "sub", "https://img"));

        SavedItemResponse row = service.getMine(null, Language.ENGLISH).get(0);

        assertThat(row.available()).isTrue();
        assertThat(row.title()).isEqualTo("Hello GenLink");
        assertThat(row.imageUrl()).isEqualTo("https://img");
    }

    /**
     * item_id is not a foreign key, so the target can vanish. The row must survive and
     * report itself unavailable rather than render blank.
     */
    @Test
    void rowIsMarkedUnavailableWhenTheTargetHasBeenDeleted() {
        when(savedItemRepository.findByUserIdOrderBySavedAtDesc(USER_ID)).thenReturn(List.of(savedPost()));
        when(previewResolver.resolve(any(), any(), any())).thenReturn(Map.of());

        SavedItemResponse row = service.getMine(null, Language.ENGLISH).get(0);

        assertThat(row.available()).isFalse();
        assertThat(row.title()).isNull();
        assertThat(row.itemId()).isEqualTo(POST_ID);
    }

    /** The whole point of hydrating here: one lookup per type, not one per item. */
    @Test
    void previewsAreBatchedPerTypeNotPerItem() {
        SavedItem a = savedPost();
        SavedItem b = savedPost();
        ReflectionTestUtils.setField(b, "itemId", UUID.randomUUID());
        when(savedItemRepository.findByUserIdOrderBySavedAtDesc(USER_ID)).thenReturn(List.of(a, b));
        when(previewResolver.resolve(any(), any(), any())).thenReturn(Map.of());

        service.getMine(null, Language.ENGLISH);

        verify(previewResolver, times(1)).resolve(eq(SavedItemType.POST), any(), any());
    }

    @Test
    void emptyListSkipsTheResolverEntirely() {
        when(savedItemRepository.findByUserIdOrderBySavedAtDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.getMine(null, Language.ENGLISH)).isEmpty();
        verify(previewResolver, never()).resolve(any(), any(), any());
    }

    private SavedItem savedPost() {
        SavedItem item = SavedItem.builder()
            .itemType(SavedItemType.POST)
            .itemId(POST_ID)
            .savedAt(LocalDateTime.now())
            .build();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        return item;
    }

    private void stubPreview(SavedItemPreview preview) {
        when(previewResolver.resolve(any(), any(), any())).thenReturn(Map.of(POST_ID, preview));
    }
}
