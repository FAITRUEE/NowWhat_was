package com.nowwhat.domain.task.dto;

import java.util.List;

public record RerankResult(List<RerankedTask> orderedTasks) {
}
