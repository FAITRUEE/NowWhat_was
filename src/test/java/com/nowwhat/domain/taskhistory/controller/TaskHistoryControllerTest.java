package com.nowwhat.domain.taskhistory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.repository.TaskRepository;
import com.nowwhat.domain.taskhistory.entity.TaskHistory;
import com.nowwhat.domain.taskhistory.repository.TaskHistoryRepository;
import com.nowwhat.domain.user.entity.User;
import com.nowwhat.domain.user.repository.UserRepository;
import com.nowwhat.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskHistoryRepository taskHistoryRepository;

    private String token;
    private User user;

    @BeforeEach
    void signUpAndLogIn() throws Exception {
        String email = "history-tests-" + System.nanoTime() + "@example.com";
        token = TestAuth.signupAndLogin(mockMvc, objectMapper, email, "password123");
        user = userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void period_week_includesACompletionFromYesterday() throws Exception {
        seedCompletedTask("어제 완료", LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/tasks/history?period=week").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("어제 완료"));
    }

    @Test
    void period_week_excludesACompletionFromTenDaysAgo_butMonthIncludesIt() throws Exception {
        seedCompletedTask("열흘 전 완료", LocalDateTime.now().minusDays(10));

        mockMvc.perform(get("/api/tasks/history?period=week").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/tasks/history?period=month").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("열흘 전 완료"));
    }

    @Test
    void history_withoutAuthHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/tasks/history?period=week")).andExpect(status().isForbidden());
    }

    private void seedCompletedTask(String title, LocalDateTime completedAt) {
        Task task = new Task(user, title, null, LocalDateTime.now().plusDays(1), 3, 30);
        task.complete();
        taskRepository.save(task);
        taskHistoryRepository.save(new TaskHistory(task, completedAt, 30));
    }
}
