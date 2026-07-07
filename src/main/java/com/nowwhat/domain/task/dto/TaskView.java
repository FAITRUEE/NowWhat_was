package com.nowwhat.domain.task.dto;

public enum TaskView {
    TODAY, WEEK, ALL;

    public static TaskView from(String value) {
        try {
            return TaskView.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
