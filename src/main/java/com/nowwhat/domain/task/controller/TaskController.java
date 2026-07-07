package com.nowwhat.domain.task.controller;

import com.nowwhat.domain.task.dto.RerankCandidate;
import com.nowwhat.domain.task.dto.RerankedTask;
import com.nowwhat.domain.task.dto.TaskCompleteRequest;
import com.nowwhat.domain.task.dto.TaskCreateRequest;
import com.nowwhat.domain.task.dto.TaskResponse;
import com.nowwhat.domain.task.dto.TaskUpdateRequest;
import com.nowwhat.domain.task.dto.TaskView;
import com.nowwhat.domain.task.service.TaskRerankService;
import com.nowwhat.domain.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final int RERANK_CANDIDATE_LIMIT = 5;

    private final TaskService taskService;
    private final TaskRerankService taskRerankService;

    public TaskController(TaskService taskService, TaskRerankService taskRerankService) {
        this.taskService = taskService;
        this.taskRerankService = taskRerankService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(userId, request));
    }

    @GetMapping
    public List<TaskResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "all") String view
    ) {
        return taskService.getTasks(userId, TaskView.from(view));
    }

    @GetMapping("/{id}")
    public TaskResponse detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return taskService.getDetail(userId, id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateRequest request
    ) {
        return taskService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        taskService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public TaskResponse complete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) TaskCompleteRequest request
    ) {
        Integer actualMinutes = request != null ? request.actualMinutes() : null;
        return taskService.complete(userId, id, actualMinutes);
    }

    @PostMapping("/rerank")
    public List<RerankedTask> rerank(@AuthenticationPrincipal Long userId) {
        List<RerankCandidate> candidates = taskService.getTopCandidates(userId, RERANK_CANDIDATE_LIMIT);
        return taskRerankService.rerank(candidates);
    }
}
