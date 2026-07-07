package com.nowwhat.domain.user.dto;

import java.time.LocalDateTime;

public record SignupResponse(Long id, String email, LocalDateTime createdAt) {
}
