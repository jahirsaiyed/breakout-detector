package com.breakoutdetector.service;

import com.breakoutdetector.model.WeeklyBar;
import com.breakoutdetector.service.LongBaseBreakoutService.FactorContribution;
import com.breakoutdetector.service.LongBaseBreakoutService.ScoredSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LongBaseBreakoutServiceTest {

    private final LongBaseBreakoutService service = new LongBaseBreakoutService();

    /** 250 weeks of a flat ~100 base on low volume, then a +10% breakout week on 3x volume. */
    private List<WeeklyBar> baseThenBreakout() {
        List<WeeklyBar> bars = new ArrayList<>();
        LocalDate d = LocalDate.of(2015, 1, 5);
        long baseVol = 1_000_000;
        for (int i = 0; i < 250; i++) {
            double c = 100 + Math.sin(i / 6.0) * 4;          // oscillates 96..104, no trend
            bars.add(new WeeklyBar(d.plusWeeks(i), c, c + 1, c - 1, c, baseVol));
        }
        // breakout week: close 115 (>5% above ~104 resistance), high 116, volume spike 3x
        bars.add(new WeeklyBar(d.plusWeeks(250), 106, 116, 105, 115, baseVol * 3));
        return bars;
    }

    @Test
    @DisplayName("detects a multi-year base breakout on volume ignition")
    void detectsBaseBreakout() {
        List<ScoredSignal> signals = service.detect(baseThenBreakout());

        assertThat(signals).hasSize(1);
        ScoredSignal s = signals.get(0);
        assertThat(s.baseWeeks()).isGreaterThanOrEqualTo(100);
        assertThat(s.volSurge()).isGreaterThanOrEqualTo(2.0);
        assertThat(s.resistance()).isBetween(103.0, 106.0);   // top of the base band
        assertThat(s.breakoutMargin()).isGreaterThan(0.05);   // cleared resistance by >5%
        assertThat(s.rankScore()).isFinite();
    }

    @Test
    @DisplayName("does not fire when the breakout lacks a volume surge")
    void noFireWithoutVolume() {
        List<WeeklyBar> bars = baseThenBreakout();
        WeeklyBar weak = bars.get(bars.size() - 1);
        bars.set(bars.size() - 1, new WeeklyBar(weak.date(), weak.open(), weak.high(),
                weak.low(), weak.close(), 1_000_000)); // same as base volume → no ignition

        assertThat(service.detect(bars)).isEmpty();
    }

    @Test
    @DisplayName("does not fire on a flat range with no breakout")
    void noFireOnFlatRange() {
        List<WeeklyBar> bars = new ArrayList<>();
        LocalDate d = LocalDate.of(2015, 1, 5);
        for (int i = 0; i < 260; i++) {
            double c = 100 + Math.sin(i / 6.0) * 4;
            bars.add(new WeeklyBar(d.plusWeeks(i), c, c + 1, c - 1, c, 1_000_000));
        }
        assertThat(service.detect(bars)).isEmpty();
    }

    @Test
    @DisplayName("factor contributions + intercept reconstruct the rank score exactly")
    void contributionsSumToScore() {
        List<ScoredSignal> signals = service.detect(baseThenBreakout());
        assertThat(signals).hasSize(1);
        ScoredSignal s = signals.get(0);

        List<FactorContribution> factors = service.explainFactors(baseThenBreakout(), s);
        assertThat(factors).hasSize(6);
        assertThat(factors).extracting(FactorContribution::label)
                .containsExactly("Base length", "Volume surge", "Base flatness",
                        "Base depth", "Breakout margin (don't-chase)", "26-week momentum");

        double reconstructed = service.intercept()
                + factors.stream().mapToDouble(FactorContribution::contribution).sum();
        // the score is a transparent linear model: intercept + sum(coef * z) == rankScore
        assertThat(reconstructed).isEqualTo(s.rankScore(), within(1e-9));
    }
}
