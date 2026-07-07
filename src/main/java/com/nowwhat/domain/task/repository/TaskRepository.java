package com.nowwhat.domain.task.repository;

import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.task.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserIdAndStatus(Long userId, TaskStatus status);

    List<Task> findByUserIdAndDeadlineBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
