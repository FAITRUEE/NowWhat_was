package com.nowwhat.domain.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record TeamTaskCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull LocalDateTime deadline,
        @Min(1) @Max(5) int importance,
        @Positive Integer estimatedMinutes,
        @NotNull Long assigneeUserId
) {
}
