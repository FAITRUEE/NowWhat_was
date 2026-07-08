package com.nowwhat.domain.team.controller;

import com.nowwhat.domain.team.dto.AddTeamMemberRequest;
import com.nowwhat.domain.team.dto.TeamBundleResponse;
import com.nowwhat.domain.team.dto.TeamCreateRequest;
import com.nowwhat.domain.team.dto.TeamResponse;
import com.nowwhat.domain.team.dto.TeamTaskCreateRequest;
import com.nowwhat.domain.team.dto.TeamTaskResponse;
import com.nowwhat.domain.team.service.TeamService;
import com.nowwhat.domain.team.service.TeamTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final TeamTaskService teamTaskService;

    public TeamController(TeamService teamService, TeamTaskService teamTaskService) {
        this.teamService = teamService;
        this.teamTaskService = teamTaskService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(userId, request.name()));
    }

    @GetMapping
    public List<TeamResponse> list(@AuthenticationPrincipal Long userId) {
        return teamService.getMyTeams(userId);
    }

    @GetMapping("/{id}")
    public TeamResponse detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return teamService.getTeamDetail(userId, id);
    }

    @PostMapping("/{id}/members")
    public TeamResponse addMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        return teamService.addMember(userId, id, request.email());
    }

    @PostMapping("/{id}/organize")
    public List<TeamBundleResponse> organize(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return teamTaskService.organizeTasks(userId, id);
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<TeamTaskResponse> createTask(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TeamTaskCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamTaskService.createTask(userId, id, request));
    }

    @GetMapping("/{id}/bundles")
    public List<TeamBundleResponse> bundles(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return teamTaskService.getBundles(userId, id);
    }

    @GetMapping("/{id}/board")
    public List<TeamTaskResponse> board(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return teamTaskService.getBoard(userId, id);
    }
}
