package com.nowwhat.domain.user.service;

import com.nowwhat.domain.task.service.TaskService;
import com.nowwhat.domain.user.dto.WeightResponse;
import com.nowwhat.domain.user.dto.WeightUpdateRequest;
import com.nowwhat.domain.user.entity.User;
import com.nowwhat.domain.user.repository.UserRepository;
import com.nowwhat.global.exception.CustomException;
import com.nowwhat.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TaskService taskService;

    public UserService(UserRepository userRepository, TaskService taskService) {
        this.userRepository = userRepository;
        this.taskService = taskService;
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public WeightResponse getWeights(Long userId) {
        User user = getById(userId);
        return new WeightResponse(user.getUrgencyWeight(), user.getImportanceWeight(), user.getEffortWeight());
    }

    @Transactional
    public WeightResponse updateWeights(Long userId, WeightUpdateRequest request) {
        User user = getById(userId);
        user.updateWeights(request.urgencyWeight(), request.importanceWeight(), request.effortWeight());
        taskService.recalculateAllScores(userId, request.urgencyWeight(), request.importanceWeight(), request.effortWeight());
        return new WeightResponse(user.getUrgencyWeight(), user.getImportanceWeight(), user.getEffortWeight());
    }
}
