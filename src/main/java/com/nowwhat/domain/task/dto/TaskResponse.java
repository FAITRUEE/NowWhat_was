package com.nowwhat.domain.task.dto;

import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.entity.TaskStatus;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDateTime deadline,
        int importance,
        Integer estimatedMinutes,
        TaskStatus status,
        Double score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDeadline(),
                task.getImportance(),
                task.getEstimatedMinutes(),
                task.getStatus(),
                task.getScore(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
