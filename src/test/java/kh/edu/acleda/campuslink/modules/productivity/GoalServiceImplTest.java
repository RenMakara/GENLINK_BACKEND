package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.MilestoneCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalMilestoneRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.GoalServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoalServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();
    private static final UUID M1 = UUID.randomUUID();
    private static final UUID M2 = UUID.randomUUID();

    @Mock private GoalRepository goalRepository;
    @Mock private GoalMilestoneRepository milestoneRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private GoalServiceImpl service;
    private User user;
    private Goal goal;

    @BeforeEach
    void setUp() {
        service = new GoalServiceImpl(goalRepository, milestoneRepository, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        goal = Goal.builder().user(user).title("Learn Spring").build();
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> {
            Goal g = i.getArgument(0);
            if (g.getId() == null) {
                ReflectionTestUtils.setField(g, "id", GOAL_ID);
            }
            return g;
        });
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
        when(milestoneRepository.save(any(GoalMilestone.class))).thenAnswer(i -> i.getArgument(0));
        when(milestoneRepository.findByGoalIdOrderByCreatedAtAsc(GOAL_ID)).thenReturn(List.of());
    }

    private GoalMilestone milestone(UUID id, boolean completed) {
        GoalMilestone m = GoalMilestone.builder().goal(goal).title("m").completed(completed).build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private void milestones(GoalMilestone... ms) {
        when(milestoneRepository.findByGoalIdOrderByCreatedAtAsc(GOAL_ID)).thenReturn(List.of(ms));
    }

    @Test
    void createStartsNotStartedWithZeroProgress() {
        GoalResponse r = service.create(new GoalCreateRequest("Learn Spring", "desc", "LEARNING", null));

        assertThat(r.status()).isEqualTo("NOT_STARTED");
        assertThat(r.progressPercentage()).isEqualByComparingTo("0.00");
        assertThat(r.totalMilestones()).isZero();
    }

    @Test
    void addingAMilestoneKeepsGoalNotStartedUntilOneIsDone() {
        milestones(milestone(M1, false));

        GoalResponse r = service.addMilestone(GOAL_ID, new MilestoneCreateRequest("Read docs"));

        verify(milestoneRepository).save(any(GoalMilestone.class));
        assertThat(r.status()).isEqualTo("NOT_STARTED");
        assertThat(r.totalMilestones()).isEqualTo(1);
    }

    @Test
    void completingOneOfTwoMilestonesIsHalfDoneAndInProgress() {
        GoalMilestone m1 = milestone(M1, false);
        GoalMilestone m2 = milestone(M2, false);
        when(milestoneRepository.findByIdAndGoalId(M1, GOAL_ID)).thenReturn(Optional.of(m1));
        milestones(m1, m2); // service marks m1 completed before recomputing

        GoalResponse r = service.setMilestoneCompleted(GOAL_ID, M1, true);

        assertThat(r.progressPercentage()).isEqualByComparingTo("50.00");
        assertThat(r.status()).isEqualTo("IN_PROGRESS");
        assertThat(r.completedMilestones()).isEqualTo(1);
    }

    @Test
    void completingAllMilestonesFlipsTheGoalToCompleted() {
        GoalMilestone m1 = milestone(M1, true);
        GoalMilestone m2 = milestone(M2, false);
        when(milestoneRepository.findByIdAndGoalId(M2, GOAL_ID)).thenReturn(Optional.of(m2));
        milestones(m1, m2);

        GoalResponse r = service.setMilestoneCompleted(GOAL_ID, M2, true);

        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.progressPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void reopeningAMilestoneDropsBackToInProgress() {
        GoalMilestone m1 = milestone(M1, true);
        GoalMilestone m2 = milestone(M2, false);
        when(milestoneRepository.findByIdAndGoalId(M1, GOAL_ID)).thenReturn(Optional.of(m1));
        milestones(m1, m2);

        GoalResponse r = service.setMilestoneCompleted(GOAL_ID, M1, false);

        assertThat(r.status()).isEqualTo("NOT_STARTED"); // both now incomplete
        assertThat(m1.getCompletedAt()).isNull();
    }

    @Test
    void deletingAMilestoneRecomputesProgress() {
        GoalMilestone m1 = milestone(M1, true);
        when(milestoneRepository.findByIdAndGoalId(M1, GOAL_ID)).thenReturn(Optional.of(m1));
        milestones(); // after delete, none remain

        GoalResponse r = service.deleteMilestone(GOAL_ID, M1);

        verify(milestoneRepository).delete(m1);
        assertThat(r.totalMilestones()).isZero();
        assertThat(r.status()).isEqualTo("NOT_STARTED");
    }

    @Test
    void milestoneOnAnotherGoalIsNotFound() {
        when(milestoneRepository.findByIdAndGoalId(M1, GOAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setMilestoneCompleted(GOAL_ID, M1, true))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteSoftDeletesTheGoal() {
        service.delete(GOAL_ID);

        assertThat(goal.getDeletedAt()).isNotNull();
    }

    @Test
    void unknownGoalReadsAsNotFound() {
        when(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(GOAL_ID)).isInstanceOf(NotFoundException.class);
    }
}
