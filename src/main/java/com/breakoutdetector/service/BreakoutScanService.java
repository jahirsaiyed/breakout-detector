package com.breakoutdetector.service;

import com.breakoutdetector.model.Candidate;
import com.breakoutdetector.model.WeeklyBar;
import com.breakoutdetector.repository.CandidateRepository;
import com.breakoutdetector.service.LongBaseBreakoutService.ScoredSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the weekly scan: for every symbol in the universe, find a recent long-base
 * breakout, apply the market-regime gate, rank with the fitted model, and persist the
 * candidates. Each scan is a dated snapshot — the basis of the live, public track record.
 */
@Service
public class BreakoutScanService {

    private static final Logger log = LoggerFactory.getLogger(BreakoutScanService.class);

    private final MarketDataService marketData;
    private final LongBaseBreakoutService detector;
    private final CandidateRepository repository;

    private final String universeResource;
    private final String benchmarkUs;
    private final String benchmarkIn;
    private final int freshnessWeeks;
    private final boolean runOnStartup;

    public BreakoutScanService(MarketDataService marketData, LongBaseBreakoutService detector,
                               CandidateRepository repository,
                               @Value("${app.scan.universe-resource:universe.txt}") String universeResource,
                               @Value("${app.scan.benchmark-us:SPY}") String benchmarkUs,
                               @Value("${app.scan.benchmark-in:^NSEI}") String benchmarkIn,
                               @Value("${app.scan.freshness-weeks:8}") int freshnessWeeks,
                               @Value("${app.scan.run-on-startup:false}") boolean runOnStartup) {
        this.marketData = marketData;
        this.detector = detector;
        this.repository = repository;
        this.universeResource = universeResource;
        this.benchmarkUs = benchmarkUs;
        this.benchmarkIn = benchmarkIn;
        this.freshnessWeeks = freshnessWeeks;
        this.runOnStartup = runOnStartup;
    }

    public record ScanSummary(LocalDate scanDate, int scanned, int failed, int candidates) {}

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            log.info("app.scan.run-on-startup=true — running initial scan");
            runScanAsync(0);
        }
    }

    /** Weekly scheduled scan (cron from app.scan.cron; set to "-" to disable). */
    @Scheduled(cron = "${app.scan.cron:-}")
    public void scheduledScan() {
        log.info("Scheduled weekly scan starting");
        runScan(0);
    }

    @Async
    public void runScanAsync(int limit) { runScan(limit); }

    /**
     * Run a scan over the universe (limit&lt;=0 means all). Returns a summary; persists candidates
     * under today's scanDate (idempotent — replaces any existing rows for today).
     */
    public ScanSummary runScan(int limit) {
        LocalDate scanDate = LocalDate.now();
        List<String> universe = loadUniverse();
        if (limit > 0 && limit < universe.size()) universe = universe.subList(0, limit);

        List<WeeklyBar> spy = fetchQuiet(benchmarkUs);
        List<WeeklyBar> nifty = fetchQuiet(benchmarkIn);

        List<Candidate> found = new ArrayList<>();
        int scanned = 0, failed = 0;
        for (String symbol : universe) {
            boolean isIN = symbol.endsWith(".NS");
            try {
                List<WeeklyBar> bars = marketData.fetchWeekly(symbol);
                if (bars.size() < LongBaseBreakoutService.MIN_BASE_WEEKS + 4) continue;
                scanned++;
                ScoredSignal latest = latestFreshBreakout(bars);
                if (latest == null) continue;
                boolean regimeOn = marketData.regimeOn(isIN ? nifty : spy, latest.date());
                if (!regimeOn) continue;   // regime gate — validated to filter toxic breakouts
                found.add(toCandidate(scanDate, symbol, isIN ? "IN" : "US", latest, true));
            } catch (Exception e) {
                failed++;
                log.debug("scan failed for {}: {}", symbol, e.getMessage());
            }
        }

        rankInPlace(found);
        repository.deleteAll(repository.findByScanDateOrderByRankScoreDesc(scanDate));
        repository.saveAll(found);
        log.info("Scan {} complete: scanned={}, failed={}, candidates={}", scanDate, scanned, failed, found.size());
        return new ScanSummary(scanDate, scanned, failed, found.size());
    }

    /**
     * Full breakdown for one symbol: the latest breakout (if any), its factor contributions,
     * the regime gate, recent context bars, and plain-English reasons.
     */
    public com.breakoutdetector.model.CandidateExplanation explain(String symbol) {
        boolean isIN = symbol.endsWith(".NS");
        String market = isIN ? "IN" : "US";
        List<WeeklyBar> bars;
        try {
            bars = marketData.fetchWeekly(symbol);
        } catch (Exception e) {
            throw new IllegalArgumentException("No data for symbol '" + symbol + "': " + e.getMessage());
        }
        if (bars.size() < LongBaseBreakoutService.MIN_BASE_WEEKS + 4) {
            return notFired(symbol, market, "Not enough history (" + bars.size() + " weekly bars) to assess a multi-year base.");
        }
        List<ScoredSignal> sigs = detector.detect(bars);
        WeeklyBar last = bars.get(bars.size() - 1);
        if (sigs.isEmpty()) {
            return notFired(symbol, market, symbol.replace(".NS", "")
                    + " has not produced a multi-year base breakout in its history.");
        }
        ScoredSignal s = sigs.get(sigs.size() - 1);
        List<WeeklyBar> bench = fetchQuiet(isIN ? benchmarkIn : benchmarkUs);
        boolean regimeOn = marketData.regimeOn(bench, s.date());
        long weeksAgo = ChronoUnit.WEEKS.between(s.date(), last.date());
        boolean fresh = weeksAgo <= freshnessWeeks;
        var factors = detector.explainFactors(bars, s);

        String ccy = isIN ? "₹" : "$";
        String sym = symbol.replace(".NS", "");
        String headline = String.format(java.util.Locale.US,
                "%s broke out above a %.1f-year base — cleared resistance %s%.2f at %s%.2f (+%.1f%%) on %.1f× volume.",
                sym, s.baseWeeks() / 52.0, ccy, s.resistance(), ccy, s.price(), s.breakoutMargin() * 100, s.volSurge());

        List<String> reasons = new ArrayList<>();
        reasons.add(String.format(java.util.Locale.US,
                "Based for ~%.1f years under resistance %s%.2f — a genuine multi-year consolidation.",
                s.baseWeeks() / 52.0, ccy, s.resistance()));
        reasons.add(String.format(java.util.Locale.US,
                "Closed +%.1f%% above that resistance — %s.", s.breakoutMargin() * 100,
                s.breakoutMargin() < 0.10 ? "near the line, not extended (good entry)" : "somewhat extended above the line"));
        reasons.add(String.format(java.util.Locale.US,
                "Breakout-week volume was %.1f× the base's typical week — real participation, not a drift.", s.volSurge()));
        reasons.add(String.format(java.util.Locale.US,
                "26-week momentum into the breakout: %+.1f%%.", s.momentum26w() * 100));
        reasons.add("Market regime " + (regimeOn ? "ON" : "OFF") + ": the benchmark index is "
                + (regimeOn ? "above" : "below") + " its 40-week trend"
                + (regimeOn ? "." : " — breakouts in falling markets underperformed in testing and are filtered out."));
        reasons.add("Signal is " + weeksAgo + " week(s) old — " + (fresh ? "fresh; it would be surfaced by a scan." : "older than the freshness window; not a live candidate."));

        int idx = s.idx();
        List<com.breakoutdetector.model.CandidateExplanation.ContextBar> ctx = new ArrayList<>();
        for (int i = Math.max(0, idx - 4); i <= Math.min(bars.size() - 1, idx + 4); i++) {
            WeeklyBar b = bars.get(i);
            ctx.add(new com.breakoutdetector.model.CandidateExplanation.ContextBar(
                    b.date(), b.close(), b.high(), b.volume(), i == idx));
        }

        return new com.breakoutdetector.model.CandidateExplanation(
                symbol, market, true, s.date(), s.price(), s.resistance(), s.baseWeeks(),
                s.baseWeeks() / 52.0, s.volSurge(), s.breakoutMargin() * 100, s.momentum26w() * 100,
                s.rankScore(), regimeOn, fresh, weeksAgo, headline, reasons, factors, ctx);
    }

    private com.breakoutdetector.model.CandidateExplanation notFired(String symbol, String market, String reason) {
        return new com.breakoutdetector.model.CandidateExplanation(
                symbol, market, false, null, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0,
                "No active long-base breakout for " + symbol.replace(".NS", "") + ".",
                List.of(reason), List.of(), List.of());
    }

    /** The most recent breakout, only if it fired within the freshness window of the latest bar. */
    private ScoredSignal latestFreshBreakout(List<WeeklyBar> bars) {
        List<ScoredSignal> sigs = detector.detect(bars);
        if (sigs.isEmpty()) return null;
        ScoredSignal latest = sigs.get(sigs.size() - 1);
        long weeks = ChronoUnit.WEEKS.between(latest.date(), bars.get(bars.size() - 1).date());
        return weeks <= freshnessWeeks ? latest : null;
    }

    /** Assign percentile rank (0..1) by fitted score across the scan's candidates. */
    private void rankInPlace(List<Candidate> found) {
        found.sort(Comparator.comparingDouble(Candidate::getRankScore));
        int n = found.size();
        for (int i = 0; i < n; i++) found.get(i).setPercentileRank(n <= 1 ? 1.0 : (double) i / (n - 1));
    }

    private Candidate toCandidate(LocalDate scanDate, String symbol, String market,
                                  ScoredSignal s, boolean regimeOn) {
        Candidate c = new Candidate();
        c.setScanDate(scanDate);
        c.setSymbol(symbol);
        c.setMarket(market);
        c.setSignalDate(s.date());
        c.setPrice(s.price());
        c.setResistanceLevel(s.resistance());
        c.setBaseWeeks(s.baseWeeks());
        c.setVolSurge(s.volSurge());
        c.setBreakoutMargin(s.breakoutMargin());
        c.setMomentum26w(s.momentum26w());
        c.setRankScore(s.rankScore());
        c.setRegimeOn(regimeOn);
        return c;
    }

    private List<WeeklyBar> fetchQuiet(String symbol) {
        try { return marketData.fetchWeekly(symbol); }
        catch (Exception e) { log.warn("benchmark fetch failed for {}: {}", symbol, e.getMessage()); return List.of(); }
    }

    List<String> loadUniverse() {
        try (var in = new ClassPathResource(universeResource).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load universe resource: " + universeResource, e);
        }
    }
}
