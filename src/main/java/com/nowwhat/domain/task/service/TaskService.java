package com.nowwhat.domain.task.service;

import com.nowwhat.domain.task.dto.RerankCandidate;
import com.nowwhat.domain.task.dto.TaskCreateRequest;
import com.nowwhat.domain.task.dto.TaskResponse;
import com.nowwhat.domain.task.dto.TaskUpdateRequest;
import com.nowwhat.domain.task.dto.TaskView;
import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.entity.TaskStatus;
import com.nowwhat.domain.task.repository.TaskRepository;
import com.nowwhat.domain.taskhistory.service.TaskHistoryService;
import com.nowwhat.domain.team.repository.TeamMemberRepository;
import com.nowwhat.domain.user.entity.User;
import com.nowwhat.domain.user.repository.UserRepository;
import com.nowwhat.global.exception.CustomException;
import com.nowwhat.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PriorityScoreCalculator priorityScoreCalculator;
    private final TaskHistoryService taskHistoryService;
    private final TeamMemberRepository teamMemberRepository;

    public TaskService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            PriorityScoreCalculator priorityScoreCalculator,
            TaskHistoryService taskHistoryService,
            TeamMemberRepository teamMemberRepository
    ) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.priorityScoreCalculator = priorityScoreCalculator;
        this.taskHistoryService = taskHistoryService;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Transactional
    public TaskResponse create(Long userId, TaskCreateRequest request) {
        User user = getUser(userId);
        Task task = new Task(
                user, request.title(), request.description(),
                request.deadline(), request.importance(), request.estimatedMinutes()
        );
        applyScore(task, user);
        return TaskResponse.from(taskRepository.save(task));
    }

    /**
     * urgency_score depends on "time remaining until deadline", so a score computed at
     * create/update time goes stale the moment time passes. Rows are fetched unordered and
     * re-scored + re-sorted here on every read instead of trusting the stored `score` column.
     */
    public List<TaskResponse> getTasks(Long userId, TaskView view) {
        User user = getUser(userId);
        return taskRepository.findByUserIdAndStatusNot(userId, TaskStatus.DONE).stream()
                .peek(task -> applyScore(task, user))
                .filter(task -> matchesView(task, view))
                .sorted(Comparator.comparing(Task::getScore, Comparator.reverseOrder()))
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse getDetail(Long userId, Long taskId) {
        Task task = getOwnedTask(userId, taskId);
        applyScore(task, task.getUser());
        return TaskResponse.from(task);
    }

    /**
     * Tasks whose deadline falls within [from, to], for calendar-grid display. Unlike
     * getTasks(), this includes DONE tasks too — a calendar cell should still show what was
     * due that day regardless of completion status.
     */
    public List<TaskResponse> getCalendarTasks(Long userId, LocalDate from, LocalDate to) {
        User user = getUser(userId);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);
        return taskRepository.findByUserIdAndDeadlineBetween(userId, start, end).stream()
                .peek(task -> applyScore(task, user))
                .sorted(Comparator.comparing(Task::getDeadline))
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = getOwnedTask(userId, taskId);
        task.update(
                request.title(), request.description(),
                request.deadline(), request.importance(), request.estimatedMinutes()
        );
        applyScore(task, task.getUser());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = getOwnedTask(userId, taskId);
        taskHistoryService.deleteHistoryFor(task);
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse complete(Long userId, Long taskId, Integer actualMinutes) {
        Task task = getOwnedTask(userId, taskId);
        task.complete();
        taskHistoryService.record(task, actualMinutes);
        return TaskResponse.from(task);
    }

    /**
     * Status changes from the shared team board: unlike {@link #getOwnedTask}, this also allows
     * any member of the task's team (not just the owner/assignee) to move a card between
     * columns, since a kanban board is meant to be editable by the whole team.
     */
    @Transactional
    public TaskResponse updateStatus(Long userId, Long taskId, TaskStatus status) {
        Task task = getAccessibleTask(userId, taskId);
        if (status == TaskStatus.DONE) {
            task.complete();
            taskHistoryService.record(task, null);
        } else {
            task.moveTo(status);
        }
        return TaskResponse.from(task);
    }

    /**
     * Change who a team task is assigned to. Any accessible team member can trigger this, not
     * just the current assignee — reassignment isn't ownership-guarded (reuses
     * {@link #getAccessibleTask}). "나로 바꾸기" is just the special case of this where
     * assigneeUserId is null/omitted, defaulting the target to the requester. When an explicit
     * assigneeUserId is given, that target must also belong to the task's team (the team owner
     * always has a membership row too, see TeamService#createTeam), so a task can't be handed to
     * someone outside the team.
     */
    @Transactional
    public TaskResponse reassign(Long userId, Long taskId, Long assigneeUserId) {
        Task task = getAccessibleTask(userId, taskId);
        Long targetUserId = assigneeUserId != null ? assigneeUserId : userId;
        Long teamId = task.getTeamId();
        if (teamId != null && !teamMemberRepository.existsByTeamIdAndUserId(teamId, targetUserId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        task.reassignTo(getUser(targetUserId));
        applyScore(task, task.getUser());
        return TaskResponse.from(task);
    }

    @Transactional
    public void recalculateAllScores(Long userId, double urgencyWeight, double importanceWeight, double effortWeight) {
        List<Task> tasks = taskRepository.findByUserIdAndStatusNot(userId, TaskStatus.DONE);
        for (Task task : tasks) {
            double score = priorityScoreCalculator.calculate(
                    task.getDeadline(), task.getImportance(), task.getEstimatedMinutes(),
                    urgencyWeight, importanceWeight, effortWeight
            );
            task.updateScore(score);
        }
    }

    private static final int MIN_RERANK_LIMIT = 1;
    private static final int MAX_RERANK_LIMIT = 20;

    /**
     * Top-N candidates for LLM rerank (FN-040), freshly scored and ranked 1-based so the
     * prompt can tell the LLM what the rule-based baseline order already is. Filtered by the
     * same view the dashboard is currently showing, so rerank reorders what the user actually
     * sees instead of always drawing from the global top-N regardless of view. limit is clamped
     * so a caller can't force an unbounded prompt into the local LLM.
     */
    public List<RerankCandidate> getTopCandidates(Long userId, int limit, TaskView view) {
        int clampedLimit = Math.max(MIN_RERANK_LIMIT, Math.min(MAX_RERANK_LIMIT, limit));
        User user = getUser(userId);
        List<Task> ranked = taskRepository.findByUserIdAndStatusNot(userId, TaskStatus.DONE).stream()
                .peek(task -> applyScore(task, user))
                .filter(task -> matchesView(task, view))
                .sorted(Comparator.comparing(Task::getScore, Comparator.reverseOrder()))
                .limit(clampedLimit)
                .toList();

        return IntStream.range(0, ranked.size())
                .mapToObj(i -> {
                    Task t = ranked.get(i);
                    return new RerankCandidate(t.getId(), t.getTitle(), t.getDeadline(), t.getImportance(), t.getEstimatedMinutes(), i + 1);
                })
                .toList();
    }

    private void applyScore(Task task, User user) {
        double score = priorityScoreCalculator.calculate(
                task.getDeadline(), task.getImportance(), task.getEstimatedMinutes(),
                user.getUrgencyWeight(), user.getImportanceWeight(), user.getEffortWeight()
        );
        task.updateScore(score);
    }

    private boolean matchesView(Task task, TaskView view) {
        return switch (view) {
            case ALL -> true;
            case TODAY -> !task.getDeadline().isAfter(LocalDate.now().atTime(LocalTime.MAX));
            case WEEK -> !task.getDeadline().isAfter(LocalDate.now().plusDays(7).atTime(LocalTime.MAX));
        };
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Task getOwnedTask(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        if (!task.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return task;
    }

    private Task getAccessibleTask(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ErrorCode.TASK_NOT_FOUND));
        if (task.isOwnedBy(userId)) {
            return task;
        }
        Long teamId = task.getTeamId();
        if (teamId != null && teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            return task;
        }
        throw new CustomException(ErrorCode.ACCESS_DENIED);
    }
}
