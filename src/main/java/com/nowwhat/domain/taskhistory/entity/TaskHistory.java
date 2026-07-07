package com.nowwhat.domain.taskhistory.entity;

import com.nowwhat.domain.task.entity.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "task_histories")
public class TaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    private Integer actualMinutes;

    protected TaskHistory() {
    }

    public TaskHistory(Task task, LocalDateTime completedAt, Integer actualMinutes) {
        this.task = task;
        this.completedAt = completedAt;
        this.actualMinutes = actualMinutes;
    }
}
