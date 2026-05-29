package com.breakoutdetector.model;

import com.breakoutdetector.service.LongBaseBreakoutService.FactorContribution;

import java.time.LocalDate;
import java.util.List;

/** Full, human-readable breakdown of why a symbol did (or did not) qualify as a candidate. */
public record CandidateExplanation(
        String symbol,
        String market,                 // "US" or "IN"
        boolean fired,                 // did a long-base breakout fire?
        LocalDate signalDate,
        double price,
        double resistance,
        int baseWeeks,
        double baseYears,
        double volSurge,
        double breakoutMarginPct,
        double momentum26wPct,
        double rankScore,
        boolean regimeOn,
        boolean fresh,                 // within the scan freshness window
        long weeksAgo,
        String headline,
        List<String> reasons,
        List<FactorContribution> factors,
        List<ContextBar> context) {

    public record ContextBar(LocalDate date, double close, double high, long volume, boolean breakoutWeek) {}
}
