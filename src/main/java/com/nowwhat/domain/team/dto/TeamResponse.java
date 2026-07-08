package com.nowwhat.domain.team.dto;

import com.nowwhat.domain.team.entity.Team;

import java.time.LocalDateTime;
import java.util.List;

public record TeamResponse(
        Long id,
        String name,
        Long ownerId,
        List<TeamMemberResponse> members,
        LocalDateTime createdAt
) {
    public static TeamResponse from(Team team, List<TeamMemberResponse> members) {
        return new TeamResponse(team.getId(), team.getName(), team.getOwner().getId(), members, team.getCreatedAt());
    }
}
