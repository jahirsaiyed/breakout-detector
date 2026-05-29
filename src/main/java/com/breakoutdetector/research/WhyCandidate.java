package com.breakoutdetector.research;

import com.breakoutdetector.model.WeeklyBar;
import com.breakoutdetector.service.LongBaseBreakoutService;
import com.breakoutdetector.service.LongBaseBreakoutService.ScoredSignal;
import com.breakoutdetector.service.MarketDataService;

import java.util.List;

/** Explains why a given symbol did/didn't fire as a long-base-breakout candidate. */
public class WhyCandidate {
    public static void main(String[] args) throws Exception {
        String symbol = args.length > 0 ? args[0] : "BEN";
        var md = new MarketDataService("research-cache");
        var detector = new LongBaseBreakoutService();

        List<WeeklyBar> bars = md.fetchWeekly(symbol);
        List<WeeklyBar> spy = md.fetchWeekly("SPY");
        WeeklyBar last = bars.get(bars.size() - 1);
        System.out.printf("%s: %d weekly bars, %s .. %s (latest close %.2f)%n",
                symbol, bars.size(), bars.get(0).date(), last.date(), last.close());

        List<ScoredSignal> sigs = detector.detect(bars);
        if (sigs.isEmpty()) { System.out.println("No breakout signals."); return; }

        System.out.println("\nAll breakout signals (point-in-time):");
        for (ScoredSignal s : sigs) {
            boolean regime = md.regimeOn(spy, s.date());
            long weeksAgo = java.time.temporal.ChronoUnit.WEEKS.between(s.date(), last.date());
            System.out.printf("  %s  base=%dwk(~%.1fy)  resistance=%.2f  breakout=%.2f (+%.1f%%)  volSurge=%.1fx  mom26=%+.1f%%  score=%.3f  regime=%s  %dwk ago%s%n",
                    s.date(), s.baseWeeks(), s.baseWeeks()/52.0, s.resistance(), s.price(),
                    s.breakoutMargin()*100, s.volSurge(), s.momentum26w()*100, s.rankScore(),
                    regime ? "ON" : "OFF", weeksAgo, weeksAgo <= 8 ? "  <-- FRESH (would be scanned)" : "");
        }

        // context: bars around the most recent signal
        ScoredSignal latest = sigs.get(sigs.size() - 1);
        int idx = latest.idx();
        System.out.printf("%nContext around latest signal (%s):%n", latest.date());
        for (int i = Math.max(0, idx - 3); i <= Math.min(bars.size() - 1, idx + 3); i++) {
            WeeklyBar b = bars.get(i);
            System.out.printf("   %s  close=%8.2f  high=%8.2f  vol=%,d%s%n",
                    b.date(), b.close(), b.high(), b.volume(), i == idx ? "   <== breakout week" : "");
        }
    }
}
