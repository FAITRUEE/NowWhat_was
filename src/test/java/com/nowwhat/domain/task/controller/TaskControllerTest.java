package com.nowwhat.domain.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void signUpAndLogIn() throws Exception {
        String email = "task-tests-" + System.nanoTime() + "@example.com";
        token = TestAuth.signupAndLogin(mockMvc, objectMapper, email, "password123");
    }

    @Test
    void create_thenDetail_roundTripsTheSameTask() throws Exception {
        String deadline = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String createBody = """
                {"title":"보고서 작성","description":"분기 보고서","deadline":"%s","importance":4,"estimatedMinutes":90}
                """.formatted(deadline);

        String response = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("보고서 작성"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.score").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        long taskId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("보고서 작성"))
                .andExpect(jsonPath("$.importance").value(4));
    }

    @Test
    void create_missingTitle_returns400() throws Exception {
        String deadline = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = """
                {"title":"","deadline":"%s","importance":3}
                """.formatted(deadline);

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withoutAuthHeader_returns401() throws Exception {
        String deadline = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = """
                {"title":"제목","deadline":"%s","importance":3}
                """.formatted(deadline);

        mockMvc.perform(post("/api/tasks").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_ordersByScoreDescending() throws Exception {
        createTask("낮은 우선순위", LocalDateTime.now().plusDays(30), 1, 240);
        createTask("높은 우선순위", LocalDateTime.now().plusHours(2), 5, 15);

        mockMvc.perform(get("/api/tasks?view=all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("높은 우선순위"))
                .andExpect(jsonPath("$[1].title").value("낮은 우선순위"));
    }

    @Test
    void complete_withActualMinutes_marksDoneAndRecordsHistory() throws Exception {
        long taskId = createTask("완료할 일", LocalDateTime.now().plusDays(1), 3, 60);

        mockMvc.perform(patch("/api/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"actualMinutes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(get("/api/tasks/history?period=month").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("완료할 일"))
                .andExpect(jsonPath("$[0].actualMinutes").value(45));
    }

    @Test
    void complete_withoutBody_fallsBackToEstimatedMinutes() throws Exception {
        long taskId = createTask("소요시간 미기록", LocalDateTime.now().plusDays(1), 3, 30);

        mockMvc.perform(patch("/api/tasks/" + taskId + "/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/history?period=month").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actualMinutes").value(30));
    }

    @Test
    void update_changesFields() throws Exception {
        long taskId = createTask("원래 제목", LocalDateTime.now().plusDays(1), 3, 30);
        String deadline = LocalDateTime.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String updateBody = """
                {"title":"바뀐 제목","deadline":"%s","importance":5,"estimatedMinutes":10}
                """.formatted(deadline);

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.importance").value(5));
    }

    @Test
    void delete_removesTheTask() throws Exception {
        long taskId = createTask("지울 일", LocalDateTime.now().plusDays(1), 3, 30);

        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void delete_aCompletedTask_alsoSucceeds_despiteItsHistoryRow() throws Exception {
        long taskId = createTask("완료 후 지울 일", LocalDateTime.now().plusDays(1), 3, 30);
        mockMvc.perform(patch("/api/tasks/" + taskId + "/complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_missingTask_returns404() throws Exception {
        mockMvc.perform(get("/api/tasks/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void detail_anotherUsersTask_returns403() throws Exception {
        long taskId = createTask("내 할 일", LocalDateTime.now().plusDays(1), 3, 30);

        String otherToken = TestAuth.signupAndLogin(
                mockMvc, objectMapper, "other-user-" + System.nanoTime() + "@example.com", "password123");

        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void calendar_returnsOnlyTasksWithinRange_includingDoneOnes() throws Exception {
        long insideRangeTaskId = createTask("범위 안", LocalDateTime.now().plusDays(2), 3, 30);
        createTask("범위 밖", LocalDateTime.now().plusDays(60), 3, 30);

        mockMvc.perform(patch("/api/tasks/" + insideRangeTaskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(7);

        mockMvc.perform(get("/api/tasks/calendar?from=" + from + "&to=" + to)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("범위 안"))
                .andExpect(jsonPath("$[0].status").value("DONE"));
    }

    @Test
    void calendar_withoutAuthHeader_returns401() throws Exception {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(7);

        mockMvc.perform(get("/api/tasks/calendar?from=" + from + "&to=" + to))
                .andExpect(status().isUnauthorized());
    }

    private long createTask(String title, LocalDateTime deadline, int importance, int estimatedMinutes) throws Exception {
        String body = """
                {"title":"%s","deadline":"%s","importance":%d,"estimatedMinutes":%d}
                """.formatted(title, deadline.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), importance, estimatedMinutes);

        String response = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }
}
