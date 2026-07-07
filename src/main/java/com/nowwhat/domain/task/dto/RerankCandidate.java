package com.nowwhat.domain.task.dto;

import java.time.LocalDateTime;

public record RerankCandidate(
        Long id,
        String title,
        LocalDateTime deadline,
        int importance,
        Integer estimatedMinutes,
        int currentRank
) {
}
