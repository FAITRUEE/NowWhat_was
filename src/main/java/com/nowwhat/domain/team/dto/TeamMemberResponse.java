package com.nowwhat.domain.team.dto;

import com.nowwhat.domain.team.entity.TeamMember;

public record TeamMemberResponse(
        Long userId,
        String email
) {
    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(member.getUser().getId(), member.getUser().getEmail());
    }
}
