package com.nowwhat.domain.task.service.ollama;

import java.util.List;
import java.util.Map;

public record OllamaChatRequest(
        String model,
        List<Map<String, String>> messages,
        String format,
        boolean stream
) {
}
