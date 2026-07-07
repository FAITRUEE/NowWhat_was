package com.nowwhat.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record WeightUpdateRequest(
        @DecimalMin("0.0") @DecimalMax("1.0") double urgencyWeight,
        @DecimalMin("0.0") @DecimalMax("1.0") double importanceWeight,
        @DecimalMin("0.0") @DecimalMax("1.0") double effortWeight
) {
}
