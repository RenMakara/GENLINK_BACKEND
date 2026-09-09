package kh.edu.acleda.campuslink.modules.productivity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.TaskReminderServiceImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReminderServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 10, 0);

    @Mock private TaskRepository taskRepository;
    @Mock private NotificationEmitter notificationEmitter;

    private TaskReminderServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new TaskReminderServiceImpl(taskRepository, notificationEmitter);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private Task task(LocalDateTime dueDate, LocalDateTime reminderTime) {
        Task task = Task.builder()
            .user(user)
            .title("Submit report")
            .dueDate(dueDate)
            .reminderTime(reminderTime)
            .completed(false)
            .build();
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        return task;
    }

    private void returnTasks(Task... tasks) {
        when(taskRepository.findForReminder(
            eq(NOW.minusMinutes(5)), eq(NOW.plusMinutes(5))))
            .thenReturn(List.of(tasks));
    }

    @Test
    void emitsReminderForAnIncompleteTaskInTheConfiguredWindow() {
        Task task = task(NOW.plusHours(2), NOW);
        returnTasks(task);

        assertThat(service.sendDueReminders(NOW)).isOne();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("PRODUCTIVITY_TASK_DUE");
        assertThat(request.type()).isEqualTo("PRODUCTIVITY_TASK");
        assertThat(request.parameters())
            .containsEntry("taskTitle", "Submit report")
            .containsEntry("dueAt", "09 Sep 2026, 12:00");
        assertThat(request.referenceType()).isEqualTo("TASK");
        assertThat(request.referenceId()).isEqualTo(TASK_ID);
        assertThat(request.dedupeKey()).isEqualTo("task-reminder:" + TASK_ID + ":" + NOW);
    }

    @Test
    void usesReminderTimeAsDueAtWhenTaskHasNoDueDate() {
        Task task = task(null, NOW);
        returnTasks(task);

        assertThat(service.sendDueReminders(NOW)).isOne();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        assertThat(captor.getValue().parameters()).containsEntry("dueAt", "09 Sep 2026, 10:00");
    }

    @Test
    void skipsCompletedDeletedAndIncompleteTasks() {
        Task completed = task(NOW, NOW);
        completed.setCompleted(true);

        Task deleted = task(NOW, NOW);
        deleted.setDeletedAt(Instant.now());

        Task withoutUser = task(NOW, NOW);
        withoutUser.setUser(null);

        returnTasks(completed, deleted, withoutUser);

        assertThat(service.sendDueReminders(NOW)).isZero();

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void isolatesAnEmitterFailurePerTask() {
        returnTasks(task(NOW, NOW));
        doThrow(new IllegalStateException("notification unavailable"))
            .when(notificationEmitter).emit(any(NotificationRequest.class));

        assertThat(service.sendDueReminders(NOW)).isZero();
    }
}
