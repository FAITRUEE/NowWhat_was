package com.nowwhat.domain.task.dto;

import com.nowwhat.domain.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(
        @NotNull TaskStatus status
) {
}
