package com.nowwhat.domain.team.dto;

import java.time.LocalDateTime;

public record TaskGroupCandidate(
        Long taskId,
        String title,
        int importance,
        LocalDateTime deadline,
        Integer estimatedMinutes,
        String assigneeEmail
) {
}
