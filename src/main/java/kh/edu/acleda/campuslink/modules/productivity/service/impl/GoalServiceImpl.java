package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.GoalCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.MilestoneCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.GoalResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.MilestoneResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.GoalMilestone;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalMilestoneRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.GoalService;
import kh.edu.acleda.campuslink.modules.productivity.service.GoalMilestoneNotifier;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalServiceImpl implements GoalService {

    private static final String NOT_STARTED = "NOT_STARTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final GoalMilestoneNotifier goalMilestoneNotifier;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public GoalServiceImpl(GoalRepository goalRepository,
                           GoalMilestoneRepository milestoneRepository,
                           GoalMilestoneNotifier goalMilestoneNotifier,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.goalMilestoneNotifier = goalMilestoneNotifier;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public GoalResponse create(GoalCreateRequest request) {
        User user = loadCurrentUser();
        Goal goal = goalRepository.save(Goal.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .goalType(request.goalType())
            .deadline(request.deadline())
            .progressPercentage(BigDecimal.ZERO)
            .status(NOT_STARTED)
            .build());
        return toResponse(goal, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoalResponse> getMine() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return goalRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
            .map(g -> toResponse(g, milestonesOf(g.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GoalResponse getById(UUID id) {
        Goal goal = loadOwned(id);
        return toResponse(goal, milestonesOf(id));
    }

    @Override
    @Transactional
    public GoalResponse update(UUID id, GoalCreateRequest request) {
        Goal goal = loadOwned(id);
        goal.setTitle(request.title());
        goal.setDescription(request.description());
        goal.setGoalType(request.goalType());
        goal.setDeadline(request.deadline());
        goalRepository.save(goal);
        return toResponse(goal, milestonesOf(id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Goal goal = loadOwned(id);
        goal.setDeletedAt(Instant.now());
        goalRepository.save(goal);
    }

    @Override
    @Transactional
    public GoalResponse addMilestone(UUID goalId, MilestoneCreateRequest request) {
        Goal goal = loadOwned(goalId);
        milestoneRepository.save(GoalMilestone.builder()
            .goal(goal).title(request.title()).completed(false).build());
        return syncAndRespond(goal);
    }

    @Override
    @Transactional
    public GoalResponse setMilestoneCompleted(UUID goalId, UUID milestoneId, boolean completed) {
        Goal goal = loadOwned(goalId);
        GoalMilestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
            .orElseThrow(() -> new NotFoundException("Milestone not found"));
        boolean wasCompleted = Boolean.TRUE.equals(milestone.getCompleted());
        milestone.setCompleted(completed);
        milestone.setCompletedAt(completed ? LocalDateTime.now() : null);
        milestoneRepository.save(milestone);
        GoalResponse response = syncAndRespond(goal);
        if (completed && !wasCompleted) {
            goalMilestoneNotifier.notifyForCompletedMilestone(
                goal, milestone, response.progressPercentage());
        }
        return response;
    }

    @Override
    @Transactional
    public GoalResponse deleteMilestone(UUID goalId, UUID milestoneId) {
        Goal goal = loadOwned(goalId);
        GoalMilestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
            .orElseThrow(() -> new NotFoundException("Milestone not found"));
        // Milestones have no soft-delete column - a real delete is correct here.
        milestoneRepository.delete(milestone);
        return syncAndRespond(goal);
    }

    /** Persists the recomputed progress/status onto the goal, then builds the response. */
    private GoalResponse syncAndRespond(Goal goal) {
        List<GoalMilestone> milestones = milestonesOf(goal.getId());
        goal.setProgressPercentage(progress(milestones));
        goal.setStatus(status(milestones));
        goalRepository.save(goal);
        return toResponse(goal, milestones);
    }

    private List<GoalMilestone> milestonesOf(UUID goalId) {
        return milestoneRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
    }

    private GoalResponse toResponse(Goal goal, List<GoalMilestone> milestones) {
        int total = milestones.size();
        int done = (int) milestones.stream().filter(m -> Boolean.TRUE.equals(m.getCompleted())).count();
        List<MilestoneResponse> milestoneDtos = milestones.stream()
            .map(m -> new MilestoneResponse(m.getId(), m.getTitle(),
                Boolean.TRUE.equals(m.getCompleted()), m.getCompletedAt()))
            .toList();
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(), goal.getGoalType(),
            goal.getDeadline(), progress(milestones), status(milestones), total, done, milestoneDtos);
    }

    private BigDecimal progress(List<GoalMilestone> milestones) {
        if (milestones.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        long done = milestones.stream().filter(m -> Boolean.TRUE.equals(m.getCompleted())).count();
        return BigDecimal.valueOf(done * 100L)
            .divide(BigDecimal.valueOf(milestones.size()), 2, RoundingMode.DOWN);
    }

    private String status(List<GoalMilestone> milestones) {
        long done = milestones.stream().filter(m -> Boolean.TRUE.equals(m.getCompleted())).count();
        if (!milestones.isEmpty() && done == milestones.size()) {
            return COMPLETED;
        }
        return done > 0 ? IN_PROGRESS : NOT_STARTED;
    }

    private Goal loadOwned(UUID id) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Goal not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
