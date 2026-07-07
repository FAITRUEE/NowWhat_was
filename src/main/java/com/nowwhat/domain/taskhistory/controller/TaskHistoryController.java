package com.nowwhat.domain.taskhistory.controller;

import com.nowwhat.domain.taskhistory.dto.HistoryPeriod;
import com.nowwhat.domain.taskhistory.dto.TaskHistoryResponse;
import com.nowwhat.domain.taskhistory.service.TaskHistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/history")
public class TaskHistoryController {

    private final TaskHistoryService taskHistoryService;

    public TaskHistoryController(TaskHistoryService taskHistoryService) {
        this.taskHistoryService = taskHistoryService;
    }

    @GetMapping
    public List<TaskHistoryResponse> getHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "week") String period
    ) {
        return taskHistoryService.getHistory(userId, HistoryPeriod.from(period));
    }
}
