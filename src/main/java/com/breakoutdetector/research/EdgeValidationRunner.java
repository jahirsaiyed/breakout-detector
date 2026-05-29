package com.breakoutdetector.research;

import com.breakoutdetector.model.BreakoutSignal;
import com.breakoutdetector.model.StockData;
import com.breakoutdetector.service.BreakoutDetectionService;
import com.breakoutdetector.service.StaircasePatternService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Point-in-time event study to validate whether the staircase / multibagger score
 * has real predictive edge.
 *
 * Honest-methodology notes:
 *  - Detection is run on a series TRUNCATED to the evaluation date (data[0..t]).
 *    This is mandatory: BreakoutDetectionService.calculateLinearRegression projects
 *    the trend line to stockDataList.size()-1, so running on the full series leaks
 *    the future. Truncation makes size()-1 == t == currentIndex, which is correct.
 *  - Prices are split/dividend ADJUSTED (the production app uses raw close, which is
 *    a real bug: splits create fake breakouts/breakdowns).
 *  - Headline metric is SPREAD vs the same universe's base rate over identical forward
 *    windows. Survivorship bias inflates both signal and base equally, so the spread
 *    is far more robust than absolute returns.
 *  - No transaction costs (irrelevant for an event study of forward returns).
 */
public class EdgeValidationRunner {

    // Mixed universe: mega-caps, mid-caps, big winners AND big laggards/losers,
    // across sectors. Survivorship is still a limitation (see caveats in output).
    private static final String[] UNIVERSE = {
        "AAPL","MSFT","GOOGL","AMZN","META","NVDA","TSLA","JPM","JNJ","V",
        "PG","HD","KO","PEP","DIS","CSCO","INTC","ORCL","CRM","ADBE",
        "AMD","NFLX","PYPL","SHOP","ROKU","ZM","SNAP","UBER","ABNB","PLTR",
        "COIN","F","GE","T","GM","BAC","XOM","CVX","WMT","MCD",
        "NKE","BA","CAT","PFE","MRK","UNH","ABT","TMO","LLY","AVGO",
        "QCOM","TXN","MU"
    };

    // Forward-return horizons in trading days (~21/mo).
    private static final int[] HORIZONS = {63, 126, 252}; // 3m, 6m, 12m
    private static final String[] HORIZON_LABELS = {"3m", "6m", "12m"};

    private static final int WARMUP_BARS = 150;          // need history before first eval
    private static final Path CACHE_DIR = Paths.get("research-cache");
    private static final Path OUT_DIR = Paths.get("research-output");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BreakoutDetectionService detector = new BreakoutDetectionService();
    private final StaircasePatternService staircase = new StaircasePatternService();

    // One evaluation row.
    static final class Event {
        String symbol; LocalDateTime date; double closeAtT; int score;
        String level; boolean hasPattern;
        double[] fwd = new double[HORIZONS.length]; // NaN if unavailable
    }

    public static void main(String[] args) throws Exception {
        new EdgeValidationRunner().run();
    }

    void run() throws Exception {
        Files.createDirectories(CACHE_DIR);
        Files.createDirectories(OUT_DIR);

        List<Event> all = new ArrayList<>();
        int symbolsOk = 0;

        for (String symbol : UNIVERSE) {
            try {
                List<StockData> series = fetchAdjustedSeries(symbol);
                if (series.size() < WARMUP_BARS + HORIZONS[0]) {
                    System.out.printf("  %-6s skipped (only %d bars)%n", symbol, series.size());
                    continue;
                }
                List<Event> events = evaluateSymbol(symbol, series);
                all.addAll(events);
                symbolsOk++;
                System.out.printf("  %-6s %4d bars, %3d monthly evaluations%n",
                        symbol, series.size(), events.size());
            } catch (Exception e) {
                System.out.printf("  %-6s ERROR: %s%n", symbol, e.getMessage());
            }
        }

        System.out.printf("%n=== Evaluated %d symbols, %d total stock-month observations ===%n",
                symbolsOk, all.size());

        writeCsv(all);
        report(all);
    }

    /** Walk forward month by month; at each month-end run detection on data[0..t]. */
    List<Event> evaluateSymbol(String symbol, List<StockData> series) {
        List<Event> out = new ArrayList<>();
        int n = series.size();
        int lastMonth = -1;

        for (int t = WARMUP_BARS; t < n; t++) {
            int month = series.get(t).getTimestamp().getYear() * 12
                    + series.get(t).getTimestamp().getMonthValue();
            // evaluate once per calendar month, at the last bar seen so far in that month
            boolean isMonthBoundary = (t + 1 >= n) ||
                    (series.get(t + 1).getTimestamp().getYear() * 12
                            + series.get(t + 1).getTimestamp().getMonthValue()) != month;
            if (!isMonthBoundary) continue;
            if (month == lastMonth) continue;
            lastMonth = month;

            // copy slice — detection sorts in place
            List<StockData> slice = new ArrayList<>(series.subList(0, t + 1));

            List<BreakoutSignal> signals = detector.detectBreakouts(slice);
            Map<String, Object> sc = staircase.getMultibaggerScore(symbol, slice, signals);

            Event ev = new Event();
            ev.symbol = symbol;
            ev.date = series.get(t).getTimestamp();
            ev.closeAtT = series.get(t).getClose().doubleValue();
            ev.score = (int) sc.getOrDefault("multibaggerScore", 0);
            ev.level = String.valueOf(sc.getOrDefault("scoreLevel", "NA"));
            ev.hasPattern = Boolean.TRUE.equals(sc.get("hasStaircasePattern"));

            for (int h = 0; h < HORIZONS.length; h++) {
                int ft = t + HORIZONS[h];
                ev.fwd[h] = (ft < n)
                        ? series.get(ft).getClose().doubleValue() / ev.closeAtT - 1.0
                        : Double.NaN;
            }
            out.add(ev);
        }
        return out;
    }

    // ---- Reporting -------------------------------------------------------

    void report(List<Event> all) {
        System.out.println("\n================ EDGE VALIDATION REPORT ================\n");
        System.out.println("Metric definitions:");
        System.out.println("  N       = stock-month observations in bucket");
        System.out.println("  mean    = average forward return");
        System.out.println("  median  = median forward return (robust to outliers)");
        System.out.println("  win%    = share with positive forward return");
        System.out.println("  >50%    = share that returned more than +50% over the horizon");
        System.out.println("  spread  = bucket mean  MINUS  universe base-rate mean (the real edge)");
        System.out.println("  spreadM = bucket median MINUS universe base-rate median\n");

        for (int hi = 0; hi < HORIZONS.length; hi++) {
            final int h = hi;
            System.out.println("---------- Horizon: " + HORIZON_LABELS[h] + " ----------");
            List<Event> withFwd = all.stream()
                    .filter(e -> !Double.isNaN(e.fwd[h])).collect(Collectors.toList());
            double baseMean = mean(withFwd, h);
            double baseMed = median(withFwd, h);
            System.out.printf("BASE RATE (all stock-months): N=%d  mean=%+.1f%%  median=%+.1f%%  win%%=%.0f%%%n%n",
                    withFwd.size(), baseMean * 100, baseMed * 100, winRate(withFwd, h) * 100);

            System.out.printf("%-18s %6s %8s %8s %6s %6s %9s %9s%n",
                    "bucket", "N", "mean", "median", "win%", ">50%", "spread", "spreadM");

            // by staircase pattern present / absent
            printBucket("pattern=YES", withFwd.stream().filter(e -> e.hasPattern).collect(Collectors.toList()), h, baseMean, baseMed);
            printBucket("pattern=no", withFwd.stream().filter(e -> !e.hasPattern).collect(Collectors.toList()), h, baseMean, baseMed);

            // by score level
            for (String lvl : new String[]{"VERY_HIGH","HIGH","MEDIUM","LOW","VERY_LOW"}) {
                printBucket("level=" + lvl,
                        withFwd.stream().filter(e -> lvl.equals(e.level)).collect(Collectors.toList()),
                        h, baseMean, baseMed);
            }
            System.out.println();
        }

        System.out.println("================ CAVEATS (read before trusting) ================");
        System.out.println(" 1. SURVIVORSHIP BIAS: universe is today's listed names; dead/delisted");
        System.out.println("    companies are absent. This lifts BOTH base rate and signal, so trust");
        System.out.println("    the SPREAD columns, not the absolute means.");
        System.out.println(" 2. Overlapping windows: monthly obs with 12m horizons overlap, so N is");
        System.out.println("    not fully independent. Treat significance as indicative, not p<0.05.");
        System.out.println(" 3. Single universe / single data vendor (Yahoo). Validate on Nifty/broader");
        System.out.println("    set + licensed data before betting the business on it.");
        System.out.println(" 4. GO/NO-GO: a real edge = pattern=YES and level=HIGH/VERY_HIGH show a");
        System.out.println("    clearly POSITIVE spread on BOTH mean and median, at the 6m+ horizon,");
        System.out.println("    with N large enough to matter (rule of thumb >100).");
    }

    void printBucket(String name, List<Event> b, int h, double baseMean, double baseMed) {
        if (b.isEmpty()) { System.out.printf("%-18s %6d%n", name, 0); return; }
        double m = mean(b, h), md = median(b, h);
        System.out.printf("%-18s %6d %7.1f%% %7.1f%% %5.0f%% %5.0f%% %+8.1f%% %+8.1f%%%n",
                name, b.size(), m * 100, md * 100, winRate(b, h) * 100, overRate(b, h, 0.50) * 100,
                (m - baseMean) * 100, (md - baseMed) * 100);
    }

    double mean(List<Event> b, int h) {
        return b.stream().mapToDouble(e -> e.fwd[h]).average().orElse(0);
    }
    double median(List<Event> b, int h) {
        double[] v = b.stream().mapToDouble(e -> e.fwd[h]).sorted().toArray();
        if (v.length == 0) return 0;
        int mid = v.length / 2;
        return v.length % 2 == 1 ? v[mid] : (v[mid - 1] + v[mid]) / 2;
    }
    double winRate(List<Event> b, int h) {
        return b.stream().filter(e -> e.fwd[h] > 0).count() / (double) b.size();
    }
    double overRate(List<Event> b, int h, double thr) {
        return b.stream().filter(e -> e.fwd[h] > thr).count() / (double) b.size();
    }

    void writeCsv(List<Event> all) throws Exception {
        StringBuilder sb = new StringBuilder("symbol,date,close,score,level,hasPattern,fwd3m,fwd6m,fwd12m\n");
        for (Event e : all) {
            sb.append(e.symbol).append(',').append(e.date.toLocalDate()).append(',')
              .append(String.format(Locale.US, "%.2f", e.closeAtT)).append(',')
              .append(e.score).append(',').append(e.level).append(',').append(e.hasPattern);
            for (int h = 0; h < HORIZONS.length; h++)
                sb.append(',').append(Double.isNaN(e.fwd[h]) ? "" : String.format(Locale.US, "%.4f", e.fwd[h]));
            sb.append('\n');
        }
        Files.writeString(OUT_DIR.resolve("events.csv"), sb.toString());
        System.out.println("Wrote " + OUT_DIR.resolve("events.csv").toAbsolutePath());
    }

    // ---- Data fetch (adjusted) + disk cache ------------------------------

    List<StockData> fetchAdjustedSeries(String symbol) throws Exception {
        Path cache = CACHE_DIR.resolve(symbol + ".json");
        String body;
        if (Files.exists(cache) && Files.size(cache) > 200) {
            body = Files.readString(cache);
        } else {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                    + "?interval=1d&range=10y&events=div%2Csplit&includeAdjustedClose=true";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
            body = resp.body();
            Files.writeString(cache, body);
            Thread.sleep(300); // be polite to Yahoo
        }
        return parseAdjusted(symbol, body);
    }

    /** Build fully split/dividend-adjusted OHLCV by scaling raw OHLC by adjClose/close. */
    List<StockData> parseAdjusted(String symbol, String body) throws Exception {
        JsonNode result = mapper.readTree(body).path("chart").path("result").get(0);
        JsonNode ts = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").get(0);
        JsonNode open = quote.path("open"), high = quote.path("high"),
                 low = quote.path("low"), close = quote.path("close"), vol = quote.path("volume");
        JsonNode adj = result.path("indicators").path("adjclose").get(0).path("adjclose");

        List<StockData> list = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            if (close.get(i).isNull() || open.get(i).isNull() || adj.get(i).isNull()
                    || vol.get(i).isNull()) continue;
            double c = close.get(i).asDouble();
            if (c <= 0) continue;
            double factor = adj.get(i).asDouble() / c; // adjustment ratio
            LocalDateTime dt = LocalDateTime.ofEpochSecond(ts.get(i).asLong(), 0, ZoneOffset.UTC);
            list.add(new StockData(symbol, dt,
                    bd(open.get(i).asDouble() * factor),
                    bd(high.get(i).asDouble() * factor),
                    bd(low.get(i).asDouble() * factor),
                    bd(c * factor),
                    vol.get(i).asLong()));
        }
        list.sort(Comparator.comparing(StockData::getTimestamp));
        return list;
    }

    static BigDecimal bd(double d) { return new BigDecimal(String.format(Locale.US, "%.4f", d)); }
}
