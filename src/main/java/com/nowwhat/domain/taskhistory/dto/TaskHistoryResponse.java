package com.nowwhat.domain.taskhistory.dto;

import com.nowwhat.domain.taskhistory.entity.TaskHistory;

import java.time.LocalDateTime;

public record TaskHistoryResponse(
        Long taskId,
        String title,
        LocalDateTime completedAt,
        Integer actualMinutes,
        Integer estimatedMinutes,
        LocalDateTime deadline
) {
    public static TaskHistoryResponse from(TaskHistory history) {
        return new TaskHistoryResponse(
                history.getTask().getId(),
                history.getTask().getTitle(),
                history.getCompletedAt(),
                history.getActualMinutes(),
                history.getTask().getEstimatedMinutes(),
                history.getTask().getDeadline()
        );
    }
}
