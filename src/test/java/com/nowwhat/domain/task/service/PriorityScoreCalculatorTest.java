package com.nowwhat.domain.task.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityScoreCalculatorTest {

    private final PriorityScoreCalculator calculator = new PriorityScoreCalculator();

    @Test
    void higherImportanceScoresHigher_allElseEqual() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);

        double lowImportance = calculator.calculate(deadline, 1, 60, 0.0, 1.0, 0.0);
        double highImportance = calculator.calculate(deadline, 5, 60, 0.0, 1.0, 0.0);

        assertThat(highImportance).isGreaterThan(lowImportance);
    }

    @Test
    void shorterEstimatedMinutesScoresHigher_allElseEqual() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);

        double longTask = calculator.calculate(deadline, 3, 240, 0.0, 0.0, 1.0);
        double shortTask = calculator.calculate(deadline, 3, 15, 0.0, 0.0, 1.0);

        assertThat(shortTask).isGreaterThan(longTask);
    }

    @Test
    void closerDeadlineScoresHigher_allElseEqual() {
        double farDeadline = calculator.calculate(LocalDateTime.now().plusDays(30), 3, 60, 1.0, 0.0, 0.0);
        double nearDeadline = calculator.calculate(LocalDateTime.now().plusHours(2), 3, 60, 1.0, 0.0, 0.0);

        assertThat(nearDeadline).isGreaterThan(farDeadline);
    }

    @Test
    void overdueDeadlineIsClampedToMaxUrgency_notNegativeOrUnbounded() {
        double dueNow = calculator.calculate(LocalDateTime.now(), 3, 60, 1.0, 0.0, 0.0);
        double overdueByAWeek = calculator.calculate(LocalDateTime.now().minusDays(7), 3, 60, 1.0, 0.0, 0.0);

        assertThat(overdueByAWeek).isEqualTo(dueNow);
    }

    @Test
    void nullEstimatedMinutesFallsBackToMidpointEffortScore() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);

        double withNullEstimate = calculator.calculate(deadline, 3, null, 0.0, 0.0, 1.0);

        assertThat(withNullEstimate).isEqualTo(0.5);
    }

    @Test
    void zeroWeightRemovesThatFactorsInfluence() {
        LocalDateTime soon = LocalDateTime.now().plusHours(1);
        LocalDateTime distant = LocalDateTime.now().plusDays(60);

        double scoreForSoon = calculator.calculate(soon, 3, 60, 0.0, 1.0, 0.0);
        double scoreForDistant = calculator.calculate(distant, 3, 60, 0.0, 1.0, 0.0);

        assertThat(scoreForSoon).isEqualTo(scoreForDistant);
    }

    @Test
    void weightsScaleTheFinalScoreProportionally() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3);

        double atFullWeight = calculator.calculate(deadline, 5, 60, 0.0, 1.0, 0.0);
        double atHalfWeight = calculator.calculate(deadline, 5, 60, 0.0, 0.5, 0.0);

        assertThat(atHalfWeight).isEqualTo(atFullWeight / 2);
    }
}
