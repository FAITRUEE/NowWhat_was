package com.nowwhat.domain.team.service;

import com.nowwhat.domain.team.dto.TeamMemberResponse;
import com.nowwhat.domain.team.dto.TeamResponse;
import com.nowwhat.domain.team.entity.Team;
import com.nowwhat.domain.team.entity.TeamMember;
import com.nowwhat.domain.team.repository.TeamMemberRepository;
import com.nowwhat.domain.team.repository.TeamRepository;
import com.nowwhat.domain.user.entity.User;
import com.nowwhat.domain.user.repository.UserRepository;
import com.nowwhat.global.exception.CustomException;
import com.nowwhat.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public TeamService(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TeamResponse createTeam(Long userId, String name) {
        User owner = getUser(userId);
        Team team = teamRepository.save(new Team(name, owner));
        teamMemberRepository.save(new TeamMember(team, owner));
        return TeamResponse.from(team, getMemberResponses(team.getId()));
    }

    @Transactional
    public TeamResponse addMember(Long userId, Long teamId, String email) {
        Team team = getTeam(teamId);
        if (!team.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        User newMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, newMember.getId())) {
            throw new CustomException(ErrorCode.TEAM_MEMBER_ALREADY_EXISTS);
        }
        teamMemberRepository.save(new TeamMember(team, newMember));
        return TeamResponse.from(team, getMemberResponses(teamId));
    }

    public List<TeamResponse> getMyTeams(Long userId) {
        return teamMemberRepository.findByUserId(userId).stream()
                .map(TeamMember::getTeam)
                .map(team -> TeamResponse.from(team, getMemberResponses(team.getId())))
                .toList();
    }

    public TeamResponse getTeamDetail(Long userId, Long teamId) {
        Team team = getTeam(teamId);
        assertMember(teamId, userId);
        return TeamResponse.from(team, getMemberResponses(teamId));
    }

    public void assertMember(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    public List<TeamMember> getMembers(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId);
    }

    public Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
    }

    private List<TeamMemberResponse> getMemberResponses(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
