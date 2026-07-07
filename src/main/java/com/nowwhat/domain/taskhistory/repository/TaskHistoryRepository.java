package com.nowwhat.domain.taskhistory.repository;

import com.nowwhat.domain.taskhistory.entity.TaskHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {
    List<TaskHistory> findByTask_User_IdAndCompletedAtAfterOrderByCompletedAtDesc(Long userId, LocalDateTime after);
}
