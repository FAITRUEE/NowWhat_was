package com.nowwhat.domain.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.task.dto.RerankCandidate;
import com.nowwhat.domain.task.dto.RerankResult;
import com.nowwhat.domain.task.dto.RerankedTask;
import com.nowwhat.domain.task.service.ollama.OllamaChatRequest;
import com.nowwhat.domain.task.service.ollama.OllamaChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskRerankService {

    private static final Logger log = LoggerFactory.getLogger(TaskRerankService.class);

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
            log.warn(
                    "LLM rerank failed, falling back to rule-based order. model={}, candidates={}",
                    model, candidates.size(), e
            );
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
                규칙 기반 점수로 순위(현재순위)가 매겨져 있다. 하지만 이 점수는 숫자만 보기 때문에
                제목이 드러내는 실제 맥락(선행 작업 여부, 진짜 긴급도, 파급 효과 등)을 반영하지 못한다.

                현재순위는 참고용 신호일 뿐 정답이 아니다. 각 항목의 제목·마감일·중요도·소요시간을
                종합적으로 판단해서, 실제로 지금 당장 처리해야 하는 순서대로 과감하게 재배치하라.
                현재순위와 다르게 판단된다면 주저하지 말고 순서를 바꾸고, 그렇게 정렬한 이유를
                각 항목마다 한 문장으로 설명하라.
                반드시 아래 JSON 형식으로만 응답하라:
                {"orderedTasks": [{"taskId": 1, "newRank": 1, "reason": "..."}]}

                할 일 목록:
                %s
                """.formatted(taskList);
    }
}
