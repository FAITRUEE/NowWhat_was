package com.nowwhat.domain.team.dto;

import java.util.List;

public record TeamBundleResponse(Long id, List<TeamTaskResponse> tasks) {
}
