package com.nowwhat.domain.team.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /organize goes through the real TeamAssignmentService, which may or may not reach an actual
 * Ollama server depending on the environment (same situation as TaskControllerTest never
 * asserting exact /rerank output) — so these tests only assert invariants that hold either way:
 * every candidate task ends up in exactly one group (enforced by TeamAssignmentService.isValid,
 * which falls back to round-robin groups otherwise). They never assert *which* group a task
 * landed in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String ownerEmail;
    private long ownerId;

    @BeforeEach
    void signUpAndLogIn() throws Exception {
        ownerEmail = "team-owner-" + System.nanoTime() + "@example.com";
        ownerToken = TestAuth.signupAndLogin(mockMvc, objectMapper, ownerEmail, "password123");
    }

    @Test
    void create_thenList_containsTheNewTeamWithOwnerAsSoleMember() throws Exception {
        long teamId = createTeam("스터디팀");

        mockMvc.perform(get("/api/teams").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId))
                .andExpect(jsonPath("$[0].members.length()").value(1))
                .andExpect(jsonPath("$[0].members[0].email").value(ownerEmail));
    }

    @Test
    void addMember_byOwner_addsTheUserByEmail() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"email\":\"" + memberEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void addMember_byNonOwner_returns403() throws Exception {
        long teamId = createTeam("팀");
        String otherToken = TestAuth.signupAndLogin(
                mockMvc, objectMapper, "outsider-" + System.nanoTime() + "@example.com", "password123");

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void addMember_unknownEmail_returns404() throws Exception {
        long teamId = createTeam("팀");

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"email\":\"no-such-user@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void addMember_alreadyAMember_returns409() throws Exception {
        long teamId = createTeam("팀");

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"email\":\"" + ownerEmail + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_MEMBER_ALREADY_EXISTS"));
    }

    @Test
    void createTask_manualAdd_assignsToTheChosenMember() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");
        addMember(teamId, memberEmail);
        long memberId = memberIdByEmail(teamId, memberEmail);

        createTask(teamId, "기획", memberId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeEmail").value(memberEmail))
                .andExpect(jsonPath("$.teamId").value(teamId));
    }

    @Test
    void createTask_withNonMemberAssignee_returns400() throws Exception {
        long teamId = createTeam("팀");

        createTask(teamId, "기획", 999999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void organize_groupsExistingTasksWithoutTouchingOwnership() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");
        addMember(teamId, memberEmail);
        long memberId = memberIdByEmail(teamId, memberEmail);

        createTask(teamId, "기획", ownerId).andExpect(status().isCreated());
        createTask(teamId, "백엔드", memberId).andExpect(status().isCreated());
        createTask(teamId, "프론트", memberId).andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/teams/" + teamId + "/organize")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode groups = objectMapper.readTree(response);

        int total = 0;
        for (JsonNode group : groups) {
            for (JsonNode task : group.get("tasks")) {
                assertThat(task.get("teamId").asLong()).isEqualTo(teamId);
                total++;
            }
        }
        assertThat(total).isEqualTo(3);

        // ownership never changes just from organizing.
        mockMvc.perform(get("/api/tasks?view=all").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // the bundles endpoint mirrors what /organize just returned.
        mockMvc.perform(get("/api/teams/" + teamId + "/bundles").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(groups.size()));

        // calling organize again with nothing new to group returns nothing.
        mockMvc.perform(post("/api/teams/" + teamId + "/organize").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reassign_anyTeamMemberCanTakeOverATask_butOutsidersCannot() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        String memberToken = TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");
        addMember(teamId, memberEmail);

        String response = createTask(teamId, "기획", ownerId)
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/reassign").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId));

        mockMvc.perform(get("/api/tasks?view=all").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId));

        String outsiderToken = TestAuth.signupAndLogin(
                mockMvc, objectMapper, "outsider-" + System.nanoTime() + "@example.com", "password123");
        mockMvc.perform(patch("/api/tasks/" + taskId + "/reassign").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void reassign_toAnExplicitTeamMember_succeeds_butToAnOutsider_isRejected() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");
        addMember(teamId, memberEmail);
        long memberId = memberIdByEmail(teamId, memberEmail);

        String response = createTask(teamId, "기획", ownerId)
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(response).get("id").asLong();

        // owner (not the current assignee's only option) picks a specific teammate as the new assignee
        mockMvc.perform(patch("/api/tasks/" + taskId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"assigneeUserId\":" + memberId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teams/" + teamId + "/board").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeEmail").value(memberEmail));

        // a userId that never joined this team must be rejected as a reassignment target
        mockMvc.perform(patch("/api/tasks/" + taskId + "/reassign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"assigneeUserId\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void board_includesScoreAndAssignee_andRejectsNonMembers() throws Exception {
        long teamId = createTeam("팀");
        createTask(teamId, "기획", ownerId).andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/" + teamId + "/board").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeEmail").value(ownerEmail))
                .andExpect(jsonPath("$[0].score").isNumber())
                .andExpect(jsonPath("$[0].status").value("TODO"));

        String otherToken = TestAuth.signupAndLogin(
                mockMvc, objectMapper, "outsider-" + System.nanoTime() + "@example.com", "password123");
        mockMvc.perform(get("/api/teams/" + teamId + "/board").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_anyTeamMemberCanMoveACard_butOutsidersCannot() throws Exception {
        long teamId = createTeam("팀");
        String memberEmail = "member-" + System.nanoTime() + "@example.com";
        String memberToken = TestAuth.signupAndLogin(mockMvc, objectMapper, memberEmail, "password123");
        addMember(teamId, memberEmail);

        String response = createTask(teamId, "기획", ownerId).andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType("application/json")
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        String outsiderToken = TestAuth.signupAndLogin(
                mockMvc, objectMapper, "outsider-" + System.nanoTime() + "@example.com", "password123");
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType("application/json")
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private long createTeam(String name) throws Exception {
        String response = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode team = objectMapper.readTree(response);
        ownerId = team.get("ownerId").asLong();
        return team.get("id").asLong();
    }

    private void addMember(long teamId, String email) throws Exception {
        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
    }

    private long memberIdByEmail(long teamId, String email) throws Exception {
        String response = mockMvc.perform(get("/api/teams/" + teamId).header("Authorization", "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode members = objectMapper.readTree(response).get("members");
        for (JsonNode member : members) {
            if (member.get("email").asText().equals(email)) {
                return member.get("userId").asLong();
            }
        }
        throw new IllegalStateException("member not found: " + email);
    }

    private org.springframework.test.web.servlet.ResultActions createTask(long teamId, String title, long assigneeUserId) throws Exception {
        String deadline = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = """
                {"title":"%s","deadline":"%s","importance":3,"estimatedMinutes":30,"assigneeUserId":%d}
                """.formatted(title, deadline, assigneeUserId);
        return mockMvc.perform(post("/api/teams/" + teamId + "/tasks")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType("application/json")
                .content(body));
    }
}
