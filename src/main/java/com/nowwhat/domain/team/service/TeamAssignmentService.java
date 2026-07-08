package com.nowwhat.domain.team.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.task.service.ollama.OllamaChatRequest;
import com.nowwhat.domain.task.service.ollama.OllamaChatResponse;
import com.nowwhat.domain.team.dto.TaskGroup;
import com.nowwhat.domain.team.dto.TaskGroupCandidate;
import com.nowwhat.domain.team.dto.TaskGroupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups a team's already-created tasks into balanced, priority-aware bundles via the same
 * local-LLM pattern as {@link com.nowwhat.domain.task.service.TaskRerankService} (Ollama + JSON-
 * forced response + lenient fallback on any failure). Unlike the old design, tasks here already
 * have real owners (set explicitly when created) — this service only reorganizes *existing* task
 * ids into logical groups for display, it never invents new tasks or reassigns anyone.
 */
@Service
public class TeamAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TeamAssignmentService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public TeamAssignmentService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.create(baseUrl);
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public List<TaskGroup> organize(List<TaskGroupCandidate> candidates, int groupCount) {
        int clampedGroupCount = Math.max(1, Math.min(groupCount, candidates.size()));
        OllamaChatRequest request = new OllamaChatRequest(
                model,
                List.of(Map.of("role", "user", "content", buildPrompt(candidates, clampedGroupCount))),
                "json",
                false
        );

        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            String content = response.message().content();
            List<TaskGroup> groups = objectMapper.readValue(content, TaskGroupResult.class).groups();
            if (!isValid(groups, candidates)) {
                throw new IllegalStateException("LLM returned groups that don't reference every task id exactly once");
            }
            return groups;
        } catch (Exception e) {
            log.warn(
                    "LLM task grouping failed, falling back to round-robin groups. model={}, candidates={}, groupCount={}",
                    model, candidates.size(), clampedGroupCount, e
            );
            return fallback(candidates, clampedGroupCount);
        }
    }

    private boolean isValid(List<TaskGroup> groups, List<TaskGroupCandidate> candidates) {
        if (groups.isEmpty() || groups.stream().anyMatch(g -> g.taskIds().isEmpty())) {
            return false;
        }
        List<Long> flattened = groups.stream().flatMap(g -> g.taskIds().stream()).toList();
        Set<Long> validIds = candidates.stream().map(TaskGroupCandidate::taskId).collect(Collectors.toSet());
        return flattened.size() == candidates.size()
                && new HashSet<>(flattened).size() == flattened.size()
                && validIds.containsAll(flattened);
    }

    private List<TaskGroup> fallback(List<TaskGroupCandidate> candidates, int groupCount) {
        List<List<Long>> buckets = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < candidates.size(); i++) {
            buckets.get(i % groupCount).add(candidates.get(i).taskId());
        }
        return buckets.stream()
                .filter(bucket -> !bucket.isEmpty())
                .map(bucket -> new TaskGroup(bucket, "라운드로빈 그룹 배분 (LLM 미사용)"))
                .toList();
    }

    private String buildPrompt(List<TaskGroupCandidate> candidates, int groupCount) {
        String taskList = candidates.stream()
                .map(c -> "- id:%d, 제목:%s, 중요도:%d, 마감일:%s, 예상소요시간:%s분, 담당자:%s".formatted(
                        c.taskId(), c.title(), c.importance(), c.deadline(),
                        c.estimatedMinutes() == null ? "미상" : c.estimatedMinutes(), c.assigneeEmail()))
                .collect(Collectors.joining("\n"));

        return """
                아래 업무 목록을 %d개의 그룹으로 나눠라. 각 그룹은 성격이 비슷하거나 우선순위가
                비슷한 업무들끼리 묶어서 팀이 한눈에 파악하기 좋게 구성하라. 담당자는 이미
                정해져 있으니 무시하고, 업무 내용과 우선순위만 보고 묶어라. 모든 id는 정확히
                한 번씩만 어느 한 그룹에 포함되어야 하며, 그룹 개수와 업무 총개수가 맞아야 한다.
                각 그룹마다 왜 이렇게 묶었는지 한 문장으로 설명하라.
                반드시 아래 JSON 형식으로만 응답하라:
                {"groups": [{"taskIds": [1,2], "reason": "..."}]}

                업무 목록:
                %s
                """.formatted(groupCount, taskList);
    }
}
