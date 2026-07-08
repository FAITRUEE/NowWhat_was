package com.nowwhat.domain.team.service;

import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.entity.TaskStatus;
import com.nowwhat.domain.task.repository.TaskRepository;
import com.nowwhat.domain.task.service.PriorityScoreCalculator;
import com.nowwhat.domain.team.dto.TaskGroup;
import com.nowwhat.domain.team.dto.TaskGroupCandidate;
import com.nowwhat.domain.team.dto.TeamBundleResponse;
import com.nowwhat.domain.team.dto.TeamTaskCreateRequest;
import com.nowwhat.domain.team.dto.TeamTaskResponse;
import com.nowwhat.domain.team.entity.Team;
import com.nowwhat.domain.team.entity.TeamMember;
import com.nowwhat.domain.team.entity.TaskBundle;
import com.nowwhat.domain.team.repository.TaskBundleRepository;
import com.nowwhat.domain.user.entity.User;
import com.nowwhat.global.exception.CustomException;
import com.nowwhat.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bridges the team domain and the task domain: manual task creation ({@link #createTask}) is the
 * only way a team task gets an owner — "역할 추천 분배" ({@link #organizeTasks}) never creates
 * tasks or reassigns anyone, it only groups already-existing tasks into {@link TaskBundle}s for
 * display, so a team can see its workload organized by priority/theme. The shared board (kanban +
 * team-priority views) shares one response, scored fresh on every read like everywhere else in
 * the task domain.
 */
@Service
public class TeamTaskService {

    private final TeamService teamService;
    private final TeamAssignmentService teamAssignmentService;
    private final TaskRepository taskRepository;
    private final TaskBundleRepository taskBundleRepository;
    private final PriorityScoreCalculator priorityScoreCalculator;

    public TeamTaskService(
            TeamService teamService,
            TeamAssignmentService teamAssignmentService,
            TaskRepository taskRepository,
            TaskBundleRepository taskBundleRepository,
            PriorityScoreCalculator priorityScoreCalculator
    ) {
        this.teamService = teamService;
        this.teamAssignmentService = teamAssignmentService;
        this.taskRepository = taskRepository;
        this.taskBundleRepository = taskBundleRepository;
        this.priorityScoreCalculator = priorityScoreCalculator;
    }

    /**
     * Groups every not-yet-bundled, not-DONE team task into AI-organized bundles ("역할 1",
     * "역할 2" cards on the board). Purely organizational — ownership never changes here.
     */
    @Transactional
    public List<TeamBundleResponse> organizeTasks(Long userId, Long teamId) {
        teamService.assertMember(teamId, userId);
        Team team = teamService.getTeam(teamId);
        List<Task> candidates = taskRepository.findByTeam_Id(teamId).stream()
                .filter(task -> task.getBundleId() == null && task.getStatus() != TaskStatus.DONE)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, Task> tasksById = candidates.stream().collect(Collectors.toMap(Task::getId, task -> task));
        List<TaskGroupCandidate> groupCandidates = candidates.stream()
                .map(task -> new TaskGroupCandidate(
                        task.getId(), task.getTitle(), task.getImportance(),
                        task.getDeadline(), task.getEstimatedMinutes(), task.getUser().getEmail()
                ))
                .toList();
        int groupCount = Math.max(1, teamService.getMembers(teamId).size());
        List<TaskGroup> groups = teamAssignmentService.organize(groupCandidates, groupCount);

        return groups.stream()
                .map(group -> {
                    TaskBundle bundle = taskBundleRepository.save(new TaskBundle(team));
                    List<TeamTaskResponse> tasks = group.taskIds().stream()
                            .map(tasksById::get)
                            .map(task -> {
                                task.assignToBundle(bundle);
                                return toResponse(task);
                            })
                            .toList();
                    return new TeamBundleResponse(bundle.getId(), tasks);
                })
                .toList();
    }

    /**
     * Manual add from the board's "+" button — the only way a team task is created, with an
     * explicit assignee chosen up front. assigneeUserId must be an existing member of the team;
     * the requester just needs to be a member themselves.
     */
    @Transactional
    public TeamTaskResponse createTask(Long userId, Long teamId, TeamTaskCreateRequest request) {
        teamService.assertMember(teamId, userId);
        Team team = teamService.getTeam(teamId);
        User assignee = findMemberUser(teamService.getMembers(teamId), request.assigneeUserId());

        Task task = new Task(
                assignee, team, request.title(), request.description(),
                request.deadline(), request.importance(), request.estimatedMinutes()
        );
        return toResponse(taskRepository.save(task));
    }

    public List<TeamBundleResponse> getBundles(Long userId, Long teamId) {
        teamService.assertMember(teamId, userId);
        return taskBundleRepository.findByTeam_Id(teamId).stream()
                .map(this::toBundleResponse)
                .toList();
    }

    public List<TeamTaskResponse> getBoard(Long userId, Long teamId) {
        teamService.assertMember(teamId, userId);
        return taskRepository.findByTeam_Id(teamId).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(TeamTaskResponse::score, Comparator.reverseOrder()))
                .toList();
    }

    private User findMemberUser(List<TeamMember> members, Long userId) {
        return members.stream()
                .map(TeamMember::getUser)
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
    }

    private TeamBundleResponse toBundleResponse(TaskBundle bundle) {
        List<TeamTaskResponse> tasks = taskRepository.findByBundle_Id(bundle.getId()).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(TeamTaskResponse::score, Comparator.reverseOrder()))
                .toList();
        return new TeamBundleResponse(bundle.getId(), tasks);
    }

    private TeamTaskResponse toResponse(Task task) {
        User owner = task.getUser();
        double score = priorityScoreCalculator.calculate(
                task.getDeadline(), task.getImportance(), task.getEstimatedMinutes(),
                owner.getUrgencyWeight(), owner.getImportanceWeight(), owner.getEffortWeight()
        );
        task.updateScore(score);
        return TeamTaskResponse.from(task, score);
    }
}
