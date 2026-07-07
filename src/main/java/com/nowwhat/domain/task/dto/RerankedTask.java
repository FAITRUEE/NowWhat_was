package com.nowwhat.domain.task.dto;

public record RerankedTask(Long taskId, Integer newRank, String reason) {
}
