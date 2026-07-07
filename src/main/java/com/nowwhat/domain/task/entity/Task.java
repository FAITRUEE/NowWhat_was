package com.nowwhat.domain.task.entity;

import com.nowwhat.domain.user.entity.User;
import com.nowwhat.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tasks")
public class Task extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(nullable = false)
    private int importance;

    private Integer estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskStatus status = TaskStatus.TODO;

    private Double score;

    protected Task() {
    }

    public Task(User user, String title, String description, LocalDateTime deadline, int importance, Integer estimatedMinutes) {
        this.user = user;
        this.title = title;
        this.description = description;
        this.deadline = deadline;
        this.importance = importance;
        this.estimatedMinutes = estimatedMinutes;
    }

    public void update(String title, String description, LocalDateTime deadline, int importance, Integer estimatedMinutes) {
        this.title = title;
        this.description = description;
        this.deadline = deadline;
        this.importance = importance;
        this.estimatedMinutes = estimatedMinutes;
    }

    public void updateScore(double score) {
        this.score = score;
    }

    public void complete() {
        this.status = TaskStatus.DONE;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
