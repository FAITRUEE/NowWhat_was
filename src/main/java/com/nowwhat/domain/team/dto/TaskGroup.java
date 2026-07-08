package com.nowwhat.domain.team.dto;

import java.util.List;

public record TaskGroup(List<Long> taskIds, String reason) {
}
