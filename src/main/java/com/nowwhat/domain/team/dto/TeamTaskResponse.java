package com.nowwhat.domain.team.dto;

import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.entity.TaskStatus;

import java.time.LocalDateTime;

public record TeamTaskResponse(
        Long id,
        String title,
        String description,
        LocalDateTime deadline,
        int importance,
        Integer estimatedMinutes,
        TaskStatus status,
        double score,
        Long teamId,
        Long assigneeUserId,
        String assigneeEmail
) {
    public static TeamTaskResponse from(Task task, double score) {
        return new TeamTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDeadline(),
                task.getImportance(),
                task.getEstimatedMinutes(),
                task.getStatus(),
                score,
                task.getTeamId(),
                task.getUser().getId(),
                task.getUser().getEmail()
        );
    }
}
