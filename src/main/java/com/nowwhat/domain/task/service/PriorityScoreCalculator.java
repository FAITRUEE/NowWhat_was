package com.nowwhat.domain.task.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * FN-020: score = w1*urgency + w2*importance + w3*effort.
 * urgency decays exponentially with days remaining (overdue = clamped to 0 days = max urgency);
 * importance is normalized from its 1~5 scale; effort favors shorter estimated_minutes.
 * All three sub-scores are kept in a comparable ~0~1 range so the weights act as real proportions.
 */
@Component
public class PriorityScoreCalculator {

    private static final double URGENCY_DECAY_RATE = 0.15;

    public double calculate(
            LocalDateTime deadline,
            int importance,
            Integer estimatedMinutes,
            double urgencyWeight,
            double importanceWeight,
            double effortWeight
    ) {
        double urgencyScore = calculateUrgencyScore(deadline);
        double importanceScore = importance / 5.0;
        double effortScore = calculateEffortScore(estimatedMinutes);

        return urgencyWeight * urgencyScore
                + importanceWeight * importanceScore
                + effortWeight * effortScore;
    }

    private double calculateUrgencyScore(LocalDateTime deadline) {
        long hoursUntilDeadline = Duration.between(LocalDateTime.now(), deadline).toHours();
        double daysUntilDeadline = Math.max(0, hoursUntilDeadline / 24.0);
        return Math.exp(-URGENCY_DECAY_RATE * daysUntilDeadline);
    }

    private double calculateEffortScore(Integer estimatedMinutes) {
        if (estimatedMinutes == null) {
            return 0.5;
        }
        return 1.0 / (1 + estimatedMinutes / 60.0);
    }
}
