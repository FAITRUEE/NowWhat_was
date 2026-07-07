package com.nowwhat.domain.taskhistory.dto;

public enum HistoryPeriod {
    WEEK, MONTH;

    public static HistoryPeriod from(String value) {
        try {
            return HistoryPeriod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WEEK;
        }
    }
}
