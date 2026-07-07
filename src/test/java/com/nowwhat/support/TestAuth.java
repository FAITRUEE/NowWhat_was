package com.nowwhat.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signs a fresh user up and logs in for real through the actual auth endpoints, so
 * integration tests exercise the same JWT issuance path a real client would.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static String signupAndLogin(MockMvc mockMvc, ObjectMapper objectMapper, String email, String password)
            throws Exception {
        String signupBody = objectMapper.writeValueAsString(new SignupPayload(email, password));
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(signupBody))
                .andExpect(status().isCreated());

        String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private record SignupPayload(String email, String password) {
    }

    private record LoginPayload(String email, String password) {
    }
}
