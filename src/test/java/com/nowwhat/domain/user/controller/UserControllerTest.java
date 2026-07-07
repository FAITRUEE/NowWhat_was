package com.nowwhat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void signUpAndLogIn() throws Exception {
        String email = "weights-tests-" + System.nanoTime() + "@example.com";
        token = TestAuth.signupAndLogin(mockMvc, objectMapper, email, "password123");
    }

    @Test
    void getWeights_returnsDefaults_forANewUser() throws Exception {
        mockMvc.perform(get("/api/users/weights").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgencyWeight").value(0.5))
                .andExpect(jsonPath("$.importanceWeight").value(0.3))
                .andExpect(jsonPath("$.effortWeight").value(0.2));
    }

    @Test
    void putWeights_thenGet_reflectsTheUpdatedValues() throws Exception {
        mockMvc.perform(put("/api/users/weights")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"urgencyWeight\":0.7,\"importanceWeight\":0.2,\"effortWeight\":0.1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgencyWeight").value(0.7));

        mockMvc.perform(get("/api/users/weights").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgencyWeight").value(0.7))
                .andExpect(jsonPath("$.importanceWeight").value(0.2))
                .andExpect(jsonPath("$.effortWeight").value(0.1));
    }

    @Test
    void putWeights_recalculatesExistingTaskScores() throws Exception {
        String deadline = LocalDateTime.now().plusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String createBody = """
                {"title":"가중치 재계산 대상","deadline":"%s","importance":5,"estimatedMinutes":30}
                """.formatted(deadline);

        String createResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        double scoreBeforeReweight = objectMapper.readTree(createResponse).get("score").asDouble();

        // All weight on importance instead of the default urgency-heavy mix should raise
        // the score of a low-urgency (due in 10 days), max-importance task.
        mockMvc.perform(put("/api/users/weights")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"urgencyWeight\":0.0,\"importanceWeight\":1.0,\"effortWeight\":0.0}"))
                .andExpect(status().isOk());

        long taskId = objectMapper.readTree(createResponse).get("id").asLong();
        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1.0));

        org.junit.jupiter.api.Assertions.assertTrue(scoreBeforeReweight < 1.0);
    }

    @Test
    void weights_withoutAuthHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/users/weights")).andExpect(status().isForbidden());
    }
}
