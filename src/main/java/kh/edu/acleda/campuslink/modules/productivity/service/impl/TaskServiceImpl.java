package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.TaskCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.TaskResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.productivity.service.TaskService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private static final String XP_SOURCE = "TASK";
    private static final int COMPLETE_XP = 5;

    private final TaskRepository taskRepository;
    private final RewardService rewardService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public TaskServiceImpl(TaskRepository taskRepository,
                           RewardService rewardService,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.taskRepository = taskRepository;
        this.rewardService = rewardService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public TaskResponse create(TaskCreateRequest request) {
        User user = loadCurrentUser();
        Task task = taskRepository.save(Task.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .priority(request.priority())
            .category(request.category())
            .dueDate(request.dueDate())
            .reminderTime(request.reminderTime())
            .completed(false)
            .build());
        return toResponse(task);
    }

    /**
     * Filters are applied in memory: a user's task list is small and bounded, so this
     * avoids a dynamic query - and the `:param IS NULL` trick that broke on PostgreSQL
     * for the opportunities date filter.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getMine(String category, String priority, Boolean completed, String scope) {
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        Predicate<Task> matches = t ->
            (category == null || category.equalsIgnoreCase(t.getCategory()))
            && (priority == null || priority.equalsIgnoreCase(t.getPriority()))
            && (completed == null || completed.equals(Boolean.TRUE.equals(t.getCompleted())))
            && scopeMatches(t, scope, now);

        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCompletedAscDueDateAsc(userId).stream()
            .filter(matches)
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public TaskResponse update(UUID id, TaskCreateRequest request) {
        Task task = loadOwned(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setCategory(request.category());
        task.setDueDate(request.dueDate());
        task.setReminderTime(request.reminderTime());
        taskRepository.save(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse setCompleted(UUID id, boolean completed) {
        Task task = loadOwned(id);
        boolean wasCompleted = Boolean.TRUE.equals(task.getCompleted());
        task.setCompleted(completed);
        taskRepository.save(task);
        // XP only on the transition to completed, so toggling does not farm XP.
        if (completed && !wasCompleted) {
            rewardService.award(loadCurrentUser(), XP_SOURCE, id, COMPLETE_XP);
        }
        return toResponse(task);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Task task = loadOwned(id);
        task.setDeletedAt(Instant.now());
        taskRepository.save(task);
    }

    private boolean scopeMatches(Task t, String scope, LocalDateTime now) {
        if (scope == null || scope.isBlank() || scope.equalsIgnoreCase("all")) {
            return true;
        }
        LocalDateTime due = t.getDueDate();
        return switch (scope.toLowerCase()) {
            case "overdue" -> due != null && due.isBefore(now) && !Boolean.TRUE.equals(t.getCompleted());
            case "today" -> due != null && due.toLocalDate().equals(LocalDate.now());
            case "upcoming" -> due != null && due.isAfter(now);
            default -> true;
        };
    }

    private TaskResponse toResponse(Task t) {
        boolean completed = Boolean.TRUE.equals(t.getCompleted());
        boolean overdue = t.getDueDate() != null && t.getDueDate().isBefore(LocalDateTime.now()) && !completed;
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(),
            t.getCategory(), t.getDueDate(), t.getReminderTime(), completed, overdue);
    }

    private Task loadOwned(UUID id) {
        return taskRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
