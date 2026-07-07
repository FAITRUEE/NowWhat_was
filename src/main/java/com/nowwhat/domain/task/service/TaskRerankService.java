package com.nowwhat.domain.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.task.dto.RerankCandidate;
import com.nowwhat.domain.task.dto.RerankResult;
import com.nowwhat.domain.task.dto.RerankedTask;
import com.nowwhat.domain.task.service.ollama.OllamaChatRequest;
import com.nowwhat.domain.task.service.ollama.OllamaChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskRerankService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public TaskRerankService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.create(baseUrl);
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public List<RerankedTask> rerank(List<RerankCandidate> candidates) {
        if (candidates.size() <= 1) {
            return fallback(candidates);
        }

        OllamaChatRequest request = new OllamaChatRequest(
                model,
                List.of(Map.of("role", "user", "content", buildPrompt(candidates))),
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
            return objectMapper.readValue(content, RerankResult.class).orderedTasks();
        } catch (Exception e) {
            return fallback(candidates);
        }
    }

    private List<RerankedTask> fallback(List<RerankCandidate> candidates) {
        return candidates.stream()
                .map(c -> new RerankedTask(c.id(), c.currentRank(), "규칙 기반 순위 유지 (LLM 미사용)"))
                .toList();
    }

    private String buildPrompt(List<RerankCandidate> candidates) {
        String taskList = candidates.stream()
                .map(c -> "- id:%d, 현재순위:%d위, 제목:%s, 마감일:%s, 중요도:%d, 예상소요시간:%s분".formatted(
                        c.id(), c.currentRank(), c.title(), c.deadline(),
                        c.importance(),
                        c.estimatedMinutes() == null ? "미상" : c.estimatedMinutes()))
                .collect(Collectors.joining("\n"));

        return """
                아래 할 일 목록은 마감일·중요도·예상 소요시간에 사용자가 설정한 가중치를 반영한
                규칙 기반 계산으로 이미 순위(현재순위)가 매겨져 있고, 점수 차이가 근소해서 재검토가
                필요한 항목들이다.
                현재순위를 기본값으로 참고하되, 실제로 먼저 하는 게 나은 순서가 다르다고 판단되면
                조정하고, 각 항목마다 그 이유를 한 문장으로 설명하라. 조정할 특별한 이유가 없다면
                현재순위를 그대로 유지하라.
                반드시 아래 JSON 형식으로만 응답하라:
                {"orderedTasks": [{"taskId": 1, "newRank": 1, "reason": "..."}]}

                할 일 목록:
                %s
                """.formatted(taskList);
    }
}
