package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.TaskServiceImpl;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock private TaskRepository taskRepository;
    @Mock private kh.edu.acleda.campuslink.modules.entertainment.service.RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private XpHistoryRepository xpHistoryRepository;

    private TaskServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new TaskServiceImpl(taskRepository, rewardService, userRepository, currentUserProvider,
            xpHistoryRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", TASK_ID);
            }
            return t;
        });
    }

    private Task task(String category, String priority, boolean completed, LocalDateTime due) {
        Task t = Task.builder().user(user).title("t").category(category).priority(priority)
            .completed(completed).dueDate(due).build();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    private void existing(Task... tasks) {
        when(taskRepository.findByUserIdAndDeletedAtIsNullOrderByCompletedAscDueDateAsc(USER_ID))
            .thenReturn(List.of(tasks));
    }

    @Test
    void createStartsIncompleteAndNotOverdueWithNoDueDate() {
        TaskResponse r = service.create(new TaskCreateRequest("Write report", null, "HIGH", "WORK", null, null));

        assertThat(r.completed()).isFalse();
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void createWithAPastDueDateIsOverdue() {
        TaskResponse r = service.create(new TaskCreateRequest("Late", null, "HIGH", "WORK",
            LocalDateTime.now().minusDays(1), null));

        assertThat(r.overdue()).isTrue();
    }

    @Test
    void getMineWithoutFiltersReturnsAll() {
        existing(task("WORK", "HIGH", false, null), task("HOME", "LOW", true, null));

        assertThat(service.getMine(null, null, null, null)).hasSize(2);
    }

    @Test
    void getMineFiltersByCategoryPriorityAndCompleted() {
        existing(task("WORK", "HIGH", false, null), task("HOME", "LOW", true, null),
            task("WORK", "LOW", false, null));

        assertThat(service.getMine("WORK", null, null, null)).hasSize(2);
        assertThat(service.getMine(null, "LOW", null, null)).hasSize(2);
        assertThat(service.getMine(null, null, true, null)).hasSize(1);
        assertThat(service.getMine("WORK", "HIGH", false, null)).hasSize(1);
    }

    @Test
    void overdueScopeReturnsOnlyPastDueIncompleteTasks() {
        existing(
            task("WORK", "HIGH", false, LocalDateTime.now().minusDays(1)),  // overdue
            task("WORK", "HIGH", true, LocalDateTime.now().minusDays(1)),   // past but done
            task("WORK", "HIGH", false, LocalDateTime.now().plusDays(1)));  // future

        List<TaskResponse> overdue = service.getMine(null, null, null, "overdue");

        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).overdue()).isTrue();
    }

    @Test
    void todayScopeReturnsTasksDueToday() {
        existing(
            task("WORK", "HIGH", false, LocalDateTime.now()),
            task("WORK", "HIGH", false, LocalDateTime.now().plusDays(2)));

        assertThat(service.getMine(null, null, null, "today")).hasSize(1);
    }

    @Test
    void completingATaskClearsOverdue() {
        Task overdue = task("WORK", "HIGH", false, LocalDateTime.now().minusDays(1));
        when(taskRepository.findByIdAndUserIdAndDeletedAtIsNull(overdue.getId(), USER_ID))
            .thenReturn(Optional.of(overdue));

        TaskResponse r = service.setCompleted(overdue.getId(), true);

        assertThat(r.completed()).isTrue();
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void completionRetriesUseTheSameXpSourceKey() {
        Task task = task("WORK", "HIGH", false, null);
        when(taskRepository.findByIdAndUserIdAndDeletedAtIsNull(task.getId(), USER_ID))
            .thenReturn(Optional.of(task));

        service.setCompleted(task.getId(), true);
        service.setCompleted(task.getId(), false);
        service.setCompleted(task.getId(), true);

        verify(rewardService, times(2)).award(user, "TASK", task.getId(), 5);
    }

    @Test
    void deleteSoftDeletes() {
        Task t = task("WORK", "HIGH", false, null);
        when(taskRepository.findByIdAndUserIdAndDeletedAtIsNull(t.getId(), USER_ID)).thenReturn(Optional.of(t));

        service.delete(t.getId());

        assertThat(t.getDeletedAt()).isNotNull();
    }

    @Test
    void unknownTaskReadsAsNotFound() {
        when(taskRepository.findByIdAndUserIdAndDeletedAtIsNull(TASK_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(TASK_ID)).isInstanceOf(NotFoundException.class);
    }
}
