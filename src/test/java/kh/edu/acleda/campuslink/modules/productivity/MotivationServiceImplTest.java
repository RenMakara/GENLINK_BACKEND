package kh.edu.acleda.campuslink.modules.productivity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.MotivationResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import kh.edu.acleda.campuslink.modules.productivity.entity.UserSavedMotivation;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.UserSavedMotivationRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.MotivationServiceImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MotivationServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MID = UUID.randomUUID();

    @Mock private MotivationContentRepository motivationRepository;
    @Mock private UserSavedMotivationRepository savedRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private MotivationServiceImpl service;
    private User user;
    private MotivationContent motivation;

    @BeforeEach
    void setUp() {
        service = new MotivationServiceImpl(motivationRepository, savedRepository, userRepository,
            currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        motivation = motivation(MID, "FOCUS", null);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(motivationRepository.findByIdAndDeletedAtIsNull(MID)).thenReturn(Optional.of(motivation));
        when(savedRepository.findByUserIdAndMotivationId(USER_ID, MID)).thenReturn(Optional.empty());
        when(savedRepository.saveAndFlush(any(UserSavedMotivation.class))).thenAnswer(i -> i.getArgument(0));
        when(savedRepository.findByUserIdOrderBySavedAtDesc(USER_ID)).thenReturn(List.of());
    }

    private MotivationContent motivation(UUID id, String category, Instant deletedAt) {
        MotivationContent m = MotivationContent.builder().titleEn("t").contentEn("c")
            .category(category).savedCount(5).deletedAt(deletedAt).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private UserSavedMotivation savedOf(MotivationContent m) {
        return UserSavedMotivation.builder().user(user).motivation(m).savedAt(LocalDateTime.now()).build();
    }

    @Test
    void savingWhenNotYetSavedInsertsAndIncrements() {
        MotivationResponse r = service.save(MID, EN);

        verify(savedRepository).saveAndFlush(any(UserSavedMotivation.class));
        verify(motivationRepository).incrementSavedCount(MID);
        assertThat(r.saved()).isTrue();
    }

    @Test
    void savingWhenAlreadySavedIsANoOp() {
        when(savedRepository.findByUserIdAndMotivationId(USER_ID, MID))
            .thenReturn(Optional.of(savedOf(motivation)));

        MotivationResponse r = service.save(MID, EN);

        verify(savedRepository, never()).saveAndFlush(any());
        verify(motivationRepository, never()).incrementSavedCount(any());
        assertThat(r.saved()).isTrue();
    }

    @Test
    void unsavingWhenSavedDeletesAndDecrements() {
        UserSavedMotivation saved = savedOf(motivation);
        when(savedRepository.findByUserIdAndMotivationId(USER_ID, MID)).thenReturn(Optional.of(saved));

        MotivationResponse r = service.unsave(MID, EN);

        verify(savedRepository).delete(saved);
        verify(motivationRepository).decrementSavedCount(MID);
        assertThat(r.saved()).isFalse();
    }

    @Test
    void unsavingWhenNotSavedIsANoOp() {
        MotivationResponse r = service.unsave(MID, EN);

        verify(savedRepository, never()).delete(any());
        verify(motivationRepository, never()).decrementSavedCount(any());
        assertThat(r.saved()).isFalse();
    }

    @Test
    void listMarksWhichAreSavedByTheUser() {
        MotivationContent m2 = motivation(UUID.randomUUID(), "MINDSET", null);
        when(motivationRepository.findByDeletedAtIsNullOrderByCreatedAtDesc())
            .thenReturn(List.of(motivation, m2));
        when(savedRepository.findByUserIdOrderBySavedAtDesc(USER_ID))
            .thenReturn(List.of(savedOf(motivation)));

        List<MotivationResponse> all = service.getAll(null, EN);

        assertThat(all).hasSize(2);
        assertThat(all.get(0).saved()).isTrue();
        assertThat(all.get(1).saved()).isFalse();
    }

    @Test
    void listByCategoryUsesTheCategoryQuery() {
        when(motivationRepository.findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc("FOCUS"))
            .thenReturn(List.of(motivation));

        assertThat(service.getAll("FOCUS", EN)).hasSize(1);
        verify(motivationRepository).findByCategoryAndDeletedAtIsNullOrderByCreatedAtDesc("FOCUS");
    }

    @Test
    void savedListExcludesDeletedMotivation() {
        MotivationContent deleted = motivation(UUID.randomUUID(), "FOCUS", Instant.now());
        when(savedRepository.findByUserIdOrderBySavedAtDesc(USER_ID))
            .thenReturn(List.of(savedOf(motivation), savedOf(deleted)));

        assertThat(service.getSaved(EN)).hasSize(1);
    }

    @Test
    void unknownMotivationReadsAsNotFound() {
        when(motivationRepository.findByIdAndDeletedAtIsNull(MID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(MID, EN)).isInstanceOf(NotFoundException.class);
    }
}
