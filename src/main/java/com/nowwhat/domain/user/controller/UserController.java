package com.nowwhat.domain.user.controller;

import com.nowwhat.domain.user.dto.WeightResponse;
import com.nowwhat.domain.user.dto.WeightUpdateRequest;
import com.nowwhat.domain.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/weights")
    public WeightResponse getWeights(@AuthenticationPrincipal Long userId) {
        return userService.getWeights(userId);
    }

    @PutMapping("/weights")
    public WeightResponse updateWeights(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WeightUpdateRequest request
    ) {
        return userService.updateWeights(userId, request);
    }
}
