package com.nowwhat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupThenLogin_returnsAccessToken() throws Exception {
        String email = "auth-happy-path@example.com";
        String body = objectMapper.writeValueAsString(new Payload(email, "password123"));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(new Payload("dup@example.com", "password123"));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String signupBody = objectMapper.writeValueAsString(new Payload("wrongpw@example.com", "password123"));
        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody))
                .andExpect(status().isCreated());

        String loginBody = objectMapper.writeValueAsString(new Payload("wrongpw@example.com", "not-the-password"));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signup_invalidEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new Payload("not-an-email", "password123"));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    private record Payload(String email, String password) {
    }
}
