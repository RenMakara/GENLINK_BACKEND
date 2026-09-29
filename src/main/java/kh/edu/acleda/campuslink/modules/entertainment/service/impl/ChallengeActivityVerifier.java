package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import org.springframework.stereotype.Component;

/** Checks a real, persisted domain event created after the user joined the challenge. */
@Component
public class ChallengeActivityVerifier {

    private final HabitCheckInRepository habitCheckInRepository;
    private final TaskRepository taskRepository;
    private final ExpenseRepository expenseRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final FocusSessionRepository focusSessionRepository;

    public ChallengeActivityVerifier(HabitCheckInRepository habitCheckInRepository,
                                     TaskRepository taskRepository,
                                     ExpenseRepository expenseRepository,
                                     QuizAttemptRepository quizAttemptRepository,
                                     EventRsvpRepository eventRsvpRepository,
                                     CommunityMemberRepository communityMemberRepository,
                                     FocusSessionRepository focusSessionRepository) {
        this.habitCheckInRepository = habitCheckInRepository;
        this.taskRepository = taskRepository;
        this.expenseRepository = expenseRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.eventRsvpRepository = eventRsvpRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.focusSessionRepository = focusSessionRepository;
    }

    public boolean hasQualifyingActivity(ChallengeType type, UUID userId, Instant joinedAt) {
        if (type == null || userId == null || joinedAt == null) return false;
        return switch (type) {
            case HABIT -> habitCheckInRepository.existsByUserIdAndCompletedTrueAndCreatedAtAfter(userId, joinedAt);
            case TASK -> taskRepository.existsByUserIdAndCompletedTrueAndCompletedAtAfter(userId, joinedAt);
            case FINANCE -> expenseRepository.existsByUserIdAndDeletedAtIsNullAndCreatedAtAfter(userId, joinedAt);
            case QUIZ -> quizAttemptRepository
                .existsByUserIdAndCompletedAtIsNotNullAndCreatedAtAfter(userId, joinedAt);
            case EVENT -> eventRsvpRepository.existsByUserIdAndStatusAndUpdatedAtAfter(
                userId, RsvpStatus.ATTENDED, joinedAt);
            case COMMUNITY -> communityMemberRepository.existsByUserIdAndStatusAndJoinedAtAfter(
                userId, MemberStatus.ACTIVE, joinedAt);
            case FOCUS -> focusSessionRepository.existsByUserIdAndStatusAndUpdatedAtAfter(
                userId, FocusSessionStatus.COMPLETED, joinedAt);
        };
    }
}
