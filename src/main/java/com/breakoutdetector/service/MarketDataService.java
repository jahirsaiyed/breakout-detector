package com.breakoutdetector.service;

import com.breakoutdetector.model.WeeklyBar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fetches split/dividend-adjusted WEEKLY bars from Yahoo and caches them on disk.
 *
 * Critical: range=max silently coarsens granularity (monthly/quarterly); we use explicit
 * period1/period2 to force true weekly. Cache files match the research harness format so
 * scans reuse the already-downloaded data and run fast/offline.
 */
@Service
public class MarketDataService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path cacheDir;

    public MarketDataService(@Value("${app.scan.cache-dir:research-cache}") String cacheDir) {
        this.cacheDir = Paths.get(cacheDir);
    }

    public List<WeeklyBar> fetchWeekly(String symbol) throws Exception {
        Files.createDirectories(cacheDir);
        Path cache = cacheDir.resolve(symbol.replace('.', '_') + "_1wk.json");
        String body;
        if (Files.exists(cache) && Files.size(cache) > 200) {
            body = Files.readString(cache);
        } else {
            long now = Instant.now().getEpochSecond();
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                    + "?period1=0&period2=" + now + "&interval=1wk&events=div%2Csplit&includeAdjustedClose=true";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode() + " for " + symbol);
            body = resp.body();
            Files.writeString(cache, body);
            Thread.sleep(300);
        }
        return parse(body);
    }

    private List<WeeklyBar> parse(String body) throws Exception {
        JsonNode result = mapper.readTree(body).path("chart").path("result").get(0);
        JsonNode ts = result.path("timestamp");
        JsonNode q = result.path("indicators").path("quote").get(0);
        JsonNode adj = result.path("indicators").path("adjclose").get(0).path("adjclose");
        JsonNode o = q.path("open"), h = q.path("high"), l = q.path("low"), c = q.path("close"), v = q.path("volume");
        List<WeeklyBar> bars = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            if (c.get(i).isNull() || o.get(i).isNull() || adj.get(i).isNull()) continue;
            double cc = c.get(i).asDouble();
            if (cc <= 0) continue;
            double f = adj.get(i).asDouble() / cc;   // adjustment factor
            long vol = v.get(i).isNull() ? 0 : v.get(i).asLong();
            LocalDate d = LocalDate.ofInstant(Instant.ofEpochSecond(ts.get(i).asLong()), ZoneOffset.UTC);
            bars.add(new WeeklyBar(d, o.get(i).asDouble() * f, h.get(i).asDouble() * f,
                    l.get(i).asDouble() * f, cc * f, vol));
        }
        bars.sort(Comparator.comparing(WeeklyBar::date));
        return bars;
    }

    /**
     * Regime check: is the benchmark above its 40-week MA at (or just before) {@code asOf}?
     * Returns true when unknown (don't over-filter).
     */
    public boolean regimeOn(List<WeeklyBar> benchmark, LocalDate asOf) {
        if (benchmark == null || benchmark.size() < 40) return true;
        int idx = -1;
        for (int i = 0; i < benchmark.size(); i++) {
            if (!benchmark.get(i).date().isAfter(asOf)) idx = i; else break;
        }
        if (idx < 40) return true;
        double sum = 0;
        for (int j = idx - 39; j <= idx; j++) sum += benchmark.get(j).close();
        return benchmark.get(idx).close() >= sum / 40.0;
    }
}
