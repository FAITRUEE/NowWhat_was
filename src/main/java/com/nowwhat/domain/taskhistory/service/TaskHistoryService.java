package com.nowwhat.domain.taskhistory.service;

import com.nowwhat.domain.task.entity.Task;
import com.nowwhat.domain.taskhistory.dto.HistoryPeriod;
import com.nowwhat.domain.taskhistory.dto.TaskHistoryResponse;
import com.nowwhat.domain.taskhistory.entity.TaskHistory;
import com.nowwhat.domain.taskhistory.repository.TaskHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskHistoryService {

    private final TaskHistoryRepository taskHistoryRepository;

    public TaskHistoryService(TaskHistoryRepository taskHistoryRepository) {
        this.taskHistoryRepository = taskHistoryRepository;
    }

    @Transactional
    public void record(Task task, Integer actualMinutes) {
        Integer minutes = actualMinutes != null ? actualMinutes : task.getEstimatedMinutes();
        taskHistoryRepository.save(new TaskHistory(task, LocalDateTime.now(), minutes));
    }

    /**
     * Deleting a Task with completion history would otherwise violate the task_histories ->
     * tasks FK; the history only makes sense in the context of a task that still exists, so it
     * goes with it.
     */
    @Transactional
    public void deleteHistoryFor(Task task) {
        taskHistoryRepository.deleteByTask(task);
    }

    public List<TaskHistoryResponse> getHistory(Long userId, HistoryPeriod period) {
        LocalDateTime since = switch (period) {
            case WEEK -> LocalDate.now().minusWeeks(1).atStartOfDay();
            case MONTH -> LocalDate.now().minusMonths(1).atStartOfDay();
        };

        return taskHistoryRepository.findByTask_User_IdAndCompletedAtAfterOrderByCompletedAtDesc(userId, since).stream()
                .map(TaskHistoryResponse::from)
                .toList();
    }
}
