package com.nowwhat.domain.team.entity;

import com.nowwhat.global.common.BaseTimeEntity;
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

/**
 * A group of role-tasks proposed together by {@code TeamAssignmentService}, claimed as one unit
 * — whoever claims the bundle takes every {@link com.nowwhat.domain.task.entity.Task} in it.
 */
@Entity
@Getter
@Table(name = "task_bundles")
public class TaskBundle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private boolean claimed = false;

    protected TaskBundle() {
    }

    public TaskBundle(Team team) {
        this.team = team;
    }

    public void claim() {
        this.claimed = true;
    }

    public Long getTeamId() {
        return this.team.getId();
    }
}
