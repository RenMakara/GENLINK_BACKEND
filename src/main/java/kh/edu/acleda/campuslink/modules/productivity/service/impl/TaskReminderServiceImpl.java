package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskReminderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds configured task reminders in a small overlap window and emits retry-safe notifications. */
@Slf4j
@Service
public class TaskReminderServiceImpl implements TaskReminderService {

    static final Duration REMINDER_WINDOW = Duration.ofMinutes(5);
    static final String REMINDER_NOTIFICATION_TEMPLATE = "PRODUCTIVITY_TASK_DUE";
    static final String REMINDER_NOTIFICATION_TYPE = "PRODUCTIVITY_TASK";
    private static final DateTimeFormatter DUE_AT_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final TaskRepository taskRepository;
    private final NotificationEmitter notificationEmitter;

    public TaskReminderServiceImpl(TaskRepository taskRepository,
                                   NotificationEmitter notificationEmitter) {
        this.taskRepository = taskRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional(readOnly = true)
    public int sendDueReminders(LocalDateTime now) {
        Objects.requireNonNull(now, "now is required");
        LocalDateTime from = now.minus(REMINDER_WINDOW);
        LocalDateTime to = now.plus(REMINDER_WINDOW);
        List<Task> tasks = taskRepository.findForReminder(from, to);

        int emitted = 0;
        for (Task task : tasks) {
            try {
                if (emitReminder(task)) {
                    emitted++;
                }
            } catch (RuntimeException ex) {
                log.error("Task reminder failed: task={}", taskId(task), ex);
            }
        }
        return emitted;
    }

    private boolean emitReminder(Task task) {
        if (task == null || task.getId() == null || task.getUser() == null
            || task.getUser().getId() == null || task.getTitle() == null || task.getTitle().isBlank()
            || task.getReminderTime() == null || Boolean.TRUE.equals(task.getCompleted())
            || task.getDeletedAt() != null) {
            log.warn("Skipping task reminder because task details are incomplete: task={}", taskId(task));
            return false;
        }

        UUID taskId = task.getId();
        LocalDateTime dueAt = task.getDueDate() == null ? task.getReminderTime() : task.getDueDate();
        notificationEmitter.emit(new NotificationRequest(
            task.getUser().getId(),
            REMINDER_NOTIFICATION_TEMPLATE,
            REMINDER_NOTIFICATION_TYPE,
            Map.of(
                "taskTitle", task.getTitle(),
                "dueAt", dueAt.format(DUE_AT_FORMAT)),
            "TASK",
            taskId,
            "task-reminder:" + taskId + ":" + task.getReminderTime()));
        return true;
    }

    private UUID taskId(Task task) {
        return task == null ? null : task.getId();
    }
}
