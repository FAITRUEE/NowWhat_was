package com.nowwhat.domain.team.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowwhat.domain.team.dto.TaskGroup;
import com.nowwhat.domain.team.dto.TaskGroupCandidate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamAssignmentServiceTest {

    // Never a real Ollama server, so every call in these tests fails fast with a connection error.
    private static final String UNREACHABLE_BASE_URL = "http://localhost:1";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(TeamAssignmentService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

    private TaskGroupCandidate candidate(long id, String title) {
        return new TaskGroupCandidate(id, title, 3, LocalDateTime.now().plusDays(3), 30, "a@example.com");
    }

    @Test
    void whenOllamaIsUnreachable_fallsBackToRoundRobinGroups_andLogsWhyInsteadOfSwallowingIt() {
        TeamAssignmentService service = new TeamAssignmentService(UNREACHABLE_BASE_URL, "test-model", new ObjectMapper());
        List<TaskGroupCandidate> candidates = List.of(
                candidate(1, "기획"), candidate(2, "백엔드"), candidate(3, "프론트"), candidate(4, "디자인")
        );

        List<TaskGroup> result = service.organize(candidates, 2);

        // round-robin over 2 groups: id[1],[3] -> group0; id[2],[4] -> group1
        assertThat(result).hasSize(2);
        assertThat(result.get(0).taskIds()).containsExactly(1L, 3L);
        assertThat(result.get(1).taskIds()).containsExactly(2L, 4L);
        assertThat(result).allSatisfy(g -> assertThat(g.reason()).isEqualTo("라운드로빈 그룹 배분 (LLM 미사용)"));

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("LLM task grouping failed");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void groupCountIsClampedToNeverExceedTheCandidateCount() {
        TeamAssignmentService service = new TeamAssignmentService(UNREACHABLE_BASE_URL, "test-model", new ObjectMapper());

        List<TaskGroup> result = service.organize(List.of(candidate(1, "기획"), candidate(2, "백엔드")), 5);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(g -> assertThat(g.taskIds()).hasSize(1));
    }
}
