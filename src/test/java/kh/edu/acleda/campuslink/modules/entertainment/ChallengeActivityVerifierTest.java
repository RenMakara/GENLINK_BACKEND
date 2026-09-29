package kh.edu.acleda.campuslink.modules.entertainment;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.ChallengeActivityVerifier;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeActivityVerifierTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant JOINED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private HabitCheckInRepository habitCheckInRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private EventRsvpRepository eventRsvpRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private FocusSessionRepository focusSessionRepository;

    private ChallengeActivityVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new ChallengeActivityVerifier(habitCheckInRepository, taskRepository, expenseRepository,
            quizAttemptRepository, eventRsvpRepository, communityMemberRepository, focusSessionRepository);
    }

    @Test
    void checksPersistedActivityForEveryChallengeType() {
        when(habitCheckInRepository.existsByUserIdAndCompletedTrueAndCreatedAtAfter(USER_ID, JOINED_AT)).thenReturn(true);
        when(taskRepository.existsByUserIdAndCompletedTrueAndCompletedAtAfter(USER_ID, JOINED_AT)).thenReturn(true);
        when(expenseRepository.existsByUserIdAndDeletedAtIsNullAndCreatedAtAfter(USER_ID, JOINED_AT)).thenReturn(true);
        when(quizAttemptRepository.existsByUserIdAndCompletedAtIsNotNullAndCreatedAtAfter(USER_ID, JOINED_AT))
            .thenReturn(true);
        when(eventRsvpRepository.existsByUserIdAndStatusAndUpdatedAtAfter(USER_ID, RsvpStatus.ATTENDED, JOINED_AT))
            .thenReturn(true);
        when(communityMemberRepository.existsByUserIdAndStatusAndJoinedAtAfter(USER_ID, MemberStatus.ACTIVE, JOINED_AT))
            .thenReturn(true);
        when(focusSessionRepository.existsByUserIdAndStatusAndUpdatedAtAfter(
            USER_ID, FocusSessionStatus.COMPLETED, JOINED_AT)).thenReturn(true);

        for (ChallengeType type : ChallengeType.values()) {
            assertThat(verifier.hasQualifyingActivity(type, USER_ID, JOINED_AT)).isTrue();
        }
        verify(habitCheckInRepository).existsByUserIdAndCompletedTrueAndCreatedAtAfter(USER_ID, JOINED_AT);
        verify(taskRepository).existsByUserIdAndCompletedTrueAndCompletedAtAfter(USER_ID, JOINED_AT);
        verify(expenseRepository).existsByUserIdAndDeletedAtIsNullAndCreatedAtAfter(USER_ID, JOINED_AT);
        verify(quizAttemptRepository)
            .existsByUserIdAndCompletedAtIsNotNullAndCreatedAtAfter(USER_ID, JOINED_AT);
        verify(eventRsvpRepository).existsByUserIdAndStatusAndUpdatedAtAfter(USER_ID, RsvpStatus.ATTENDED, JOINED_AT);
        verify(communityMemberRepository).existsByUserIdAndStatusAndJoinedAtAfter(USER_ID, MemberStatus.ACTIVE, JOINED_AT);
        verify(focusSessionRepository).existsByUserIdAndStatusAndUpdatedAtAfter(
            USER_ID, FocusSessionStatus.COMPLETED, JOINED_AT);
    }

    @Test
    void rejectsMissingJoinTimeRatherThanAcceptingOldActivity() {
        assertThat(verifier.hasQualifyingActivity(ChallengeType.TASK, USER_ID, null)).isFalse();
    }
}
