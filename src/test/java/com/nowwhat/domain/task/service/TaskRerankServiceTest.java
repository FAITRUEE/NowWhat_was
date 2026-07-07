package com.nowwhat.domain.task.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.task.dto.RerankCandidate;
import com.nowwhat.domain.task.dto.RerankedTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRerankServiceTest {

    // Never a real Ollama server, so every call in these tests fails fast with a connection error.
    private static final String UNREACHABLE_BASE_URL = "http://localhost:1";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(TaskRerankService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    @Test
    void whenOllamaIsUnreachable_fallsBackToRuleBasedOrder_andLogsWhyInsteadOfSwallowingIt() {
        TaskRerankService service = new TaskRerankService(UNREACHABLE_BASE_URL, "test-model", new ObjectMapper());
        List<RerankCandidate> candidates = List.of(
                new RerankCandidate(1L, "첫번째", LocalDateTime.now().plusDays(1), 5, 30, 1),
                new RerankCandidate(2L, "두번째", LocalDateTime.now().plusDays(2), 3, 60, 2)
        );

        List<RerankedTask> result = service.rerank(candidates);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).taskId()).isEqualTo(1L);
        assertThat(result.get(0).newRank()).isEqualTo(1);
        assertThat(result.get(0).reason()).isEqualTo("규칙 기반 순위 유지 (LLM 미사용)");

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("LLM rerank failed");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void singleCandidate_skipsTheLlmCallEntirely_noFailureNoLogNoise() {
        TaskRerankService service = new TaskRerankService(UNREACHABLE_BASE_URL, "test-model", new ObjectMapper());
        List<RerankCandidate> single = List.of(
                new RerankCandidate(1L, "혼자", LocalDateTime.now().plusDays(1), 5, 30, 1)
        );

        List<RerankedTask> result = service.rerank(single);

        assertThat(result).hasSize(1);
        assertThat(logAppender.list).isEmpty();
    }
}
