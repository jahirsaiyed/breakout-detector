package com.breakoutdetector.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Long-Base Breakout + Breakout-and-Retest detector & validation
 * (weekly, split/dividend adjusted, point-in-time).
 *
 *  BREAKOUT entry: multi-year flat base ignites above multi-year resistance on volume (Stage-2).
 *  RETEST   entry: after such a breakout, price pulls back to the broken resistance (now support)
 *                  OR the rising 40-week MA, holds, and resumes — the AMD setup. Filters false
 *                  breakouts and gives a defined-risk re-entry.
 *
 * Detection references only bars <= i (no look-ahead). Forward returns are for validation only.
 * Universe is loaded from research-cache/universe.txt (one Yahoo symbol per line) if present,
 * else a small built-in list. Headline metric is the spread vs the universe base rate.
 */
public class LongBaseBreakoutRunner {

    // ---- breakout detector params ---------------------------------------
    static final int    MIN_BASE_WEEKS  = 100;
    static final int[]  BASE_WINDOWS    = {520, 400, 300, 200, 150, 100};
    static final double BREAKOUT_BUFFER = 0.05;
    static final double VOL_MULT        = 2.0;
    static final double FLAT_SLOPE_MAX  = 0.15;
    static final double MAX_BASE_RANGE  = 6.0;
    static final int    BREAKOUT_COOLDOWN = 52;

    // ---- retest params --------------------------------------------------
    static final int    RETEST_MIN_WEEKS = 3;
    static final int    RETEST_MAX_WEEKS = 156;   // up to 3y after breakout (AMD 2018->2022)
    static final double RETEST_BAND      = 0.05;  // low must dip within 5% above support
    static final double RETEST_FAIL      = 0.07;  // close >7% below support = failed, abort
    static final int    RETEST_COOLDOWN  = 26;    // weeks between retests of one breakout
    static final int    MAX_RETESTS      = 3;

    // ---- validation horizons (weeks) ------------------------------------
    static final int[]  HORIZONS = {26, 52, 104, 156};
    static final String[] HLBL   = {"6m", "1y", "2y", "3y"};

    static final String[] FALLBACK_UNIVERSE = {"PFC.NS","AMD","NVDA","ITC.NS","SMCI","AAPL","INTC","F"};

    static final Path CACHE = Paths.get("research-cache");
    static final Path OUT = Paths.get("research-output");
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    final ObjectMapper mapper = new ObjectMapper();

    record Bar(LocalDate date, double o, double h, double l, double c, long v) {}
    record Signal(String symbol, LocalDate date, String type, int idx,
                  double price, double refLevel, int baseWeeks, double volSurge, int score) {}
    record Obs(double[] fwd, int score, boolean regimeOn) {}
    record FitRow(LocalDate date, double[] x, double y, double[] fwd) {}
    record Model(double[] beta, double[] mean, double[] std) {}
    static final String[] FEATURES = {"baseWeeks","volSurge","slope","baseDepth","brkMargin","mom26"};

    public static void main(String[] args) throws Exception { new LongBaseBreakoutRunner().run(); }

    void run() throws Exception {
        Files.createDirectories(CACHE);
        Files.createDirectories(OUT);
        List<String> universe = loadUniverse();
        System.out.println("Universe size: " + universe.size());

        // ---- sanity checks: PFC + AMD ----
        for (String s : new String[]{"PFC.NS", "AMD"}) {
            System.out.println("\n===== SANITY: " + s + " =====");
            try {
                List<Bar> bars = fetchWeekly(s);
                System.out.printf("%s weekly bars: %d (%s..%s)%n", s, bars.size(),
                        bars.get(0).date(), bars.get(bars.size()-1).date());
                for (Signal sig : detect(s, bars))
                    System.out.printf("  %-8s %s  price=%.2f  ref=%.2f  base=%dwk(~%.1fy)  vol=%.1fx  score=%d%n",
                            sig.type(), sig.date(), sig.price(), sig.refLevel(),
                            sig.baseWeeks(), sig.baseWeeks()/52.0, sig.volSurge(), sig.score());
            } catch (Exception e) { System.out.println("  ERROR " + e.getMessage()); }
        }

        // ---- market regime maps (index above its 40-week MA) ----
        TreeMap<LocalDate,Boolean> regimeUS = buildRegime("SPY");
        TreeMap<LocalDate,Boolean> regimeIN = buildRegime("^NSEI");
        System.out.printf("Regime maps: SPY=%d weeks, ^NSEI=%d weeks%n", regimeUS.size(), regimeIN.size());

        // ---- universe event study ----
        System.out.println("\n===== UNIVERSE VALIDATION (" + universe.size() + " symbols) =====");
        List<double[]> base = new ArrayList<>();
        List<double[]> brk = new ArrayList<>(), ret = new ArrayList<>();
        List<Obs> brkObs = new ArrayList<>();
        List<FitRow> fitRows = new ArrayList<>();   // regime-ON breakouts with valid 1y outcome
        List<Signal> allSignals = new ArrayList<>();
        int ok = 0, fail = 0;

        for (String sym : universe) {
            boolean isIN = sym.endsWith(".NS");
            TreeMap<LocalDate,Boolean> regime = isIN ? regimeIN : regimeUS;
            try {
                List<Bar> bars = fetchWeekly(sym);
                if (bars.size() < MIN_BASE_WEEKS + HORIZONS[0]) continue;
                ok++;
                for (int i = MIN_BASE_WEEKS; i < bars.size(); i += 8) base.add(forward(bars, i));
                for (Signal s : detect(sym, bars)) {
                    allSignals.add(s);
                    double[] f = forward(bars, s.idx());
                    if (s.type().equals("BREAKOUT")) {
                        brk.add(f);
                        boolean rOn = regimeOn(regime, s.date());
                        brkObs.add(new Obs(f, s.score(), rOn));
                        if (rOn && !Double.isNaN(f[1]))           // 1y outcome available
                            fitRows.add(new FitRow(s.date(), features(bars, s.idx(), s.baseWeeks(), s.refLevel()),
                                    Math.log(1 + f[1]), f));
                    } else ret.add(f);
                }
            } catch (Exception e) { fail++; }
            if (ok % 100 == 0 && ok > 0) System.out.printf("  ...processed %d ok, %d failed%n", ok, fail);
        }

        System.out.printf("%nProcessed %d symbols (%d failed). Breakout signals=%d, Retest signals=%d, base samples=%d%n",
                ok, fail, brk.size(), ret.size(), base.size());
        writeSignals(allSignals);
        report(base, brk, ret);
        reportSelectivity(base, brkObs);
        fitAndTest(base, fitRows);
        walkForward(base, fitRows);
    }

    /** Fit standardized OLS ranking model on a training set. */
    Model fit(List<FitRow> train) {
        int k = FEATURES.length;
        double[] mean = new double[k], std = new double[k];
        for (FitRow r : train) for (int j=0;j<k;j++) mean[j]+=r.x()[j];
        for (int j=0;j<k;j++) mean[j]/=train.size();
        for (FitRow r : train) for (int j=0;j<k;j++) std[j]+=Math.pow(r.x()[j]-mean[j],2);
        for (int j=0;j<k;j++){ std[j]=Math.sqrt(std[j]/train.size()); if (std[j]==0) std[j]=1; }
        double[][] X = new double[train.size()][k]; double[] Y = new double[train.size()];
        for (int n=0;n<train.size();n++){ Y[n]=train.get(n).y(); for(int j=0;j<k;j++) X[n][j]=(train.get(n).x()[j]-mean[j])/std[j]; }
        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        ols.newSampleData(Y, X);
        return new Model(ols.estimateRegressionParameters(), mean, std);
    }
    double score(Model m, double[] x) {
        double s = m.beta()[0];
        for (int j=0;j<FEATURES.length;j++) s += m.beta()[j+1]*((x[j]-m.mean()[j])/m.std()[j]);
        return s;
    }

    /** Walk-forward: refit each year on all prior data, test on that year's signals. */
    void walkForward(List<double[]> base, List<FitRow> rows) {
        System.out.println("\n================ WALK-FORWARD (expanding window, refit each year) ================");
        System.out.printf("%-6s %6s %6s | %-10s | %-18s | %-18s | %s%n",
                "test", "train", "test", "yrBreak", "TOP 1/3 (best)", "BOT 1/3 (worst)", "TOP-BOT");
        System.out.printf("%-6s %6s %6s | %-10s | %8s %8s | %8s %8s | %8s%n",
                "year", "N", "N", "med 1y", "med 1y", "win%", "med 1y", "win%", "gap");
        int[] years = {2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024};
        List<Double> poolTop = new ArrayList<>(), poolBot = new ArrayList<>();
        int winsTopBeatsBot = 0, evaluated = 0;
        for (int y : years) {
            List<FitRow> train = new ArrayList<>(), test = new ArrayList<>();
            for (FitRow r : rows) { if (r.date().getYear() < y) train.add(r); else if (r.date().getYear()==y) test.add(r); }
            if (train.size() < 150 || test.size() < 18) {
                System.out.printf("%-6d %6d %6d | (skip: insufficient)%n", y, train.size(), test.size()); continue;
            }
            Model m = fit(train);
            double[] sc = new double[test.size()];
            Integer[] order = new Integer[test.size()];
            for (int n=0;n<test.size();n++){ sc[n]=score(m, test.get(n).x()); order[n]=n; }
            Arrays.sort(order,(a,b)->Double.compare(sc[b],sc[a]));
            int third = Math.max(1, test.size()/3);
            double[] top = pick(test, order, 0, third), bot = pick(test, order, test.size()-third, test.size());
            double[] all = test.stream().mapToDouble(r->r.fwd()[1]).toArray();
            double gap = (median(top)-median(bot))*100;
            for (double d : top) poolTop.add(d); for (double d : bot) poolBot.add(d);
            if (median(top) > median(bot)) winsTopBeatsBot++; evaluated++;
            System.out.printf("%-6d %6d %6d | %+8.1f%% | %+7.1f%% %5.0f%% | %+7.1f%% %5.0f%% | %+7.1f%%%n",
                    y, train.size(), test.size(), median(all)*100,
                    median(top)*100, winp(top)*100, median(bot)*100, winp(bot)*100, gap);
        }
        double[] pt = poolTop.stream().mapToDouble(Double::doubleValue).toArray();
        double[] pb = poolBot.stream().mapToDouble(Double::doubleValue).toArray();
        System.out.printf("%nPOOLED out-of-sample: TOP median=%+.1f%% (win %.0f%%, >100%%=%.0f%%)  vs  BOT median=%+.1f%% (win %.0f%%)%n",
                median(pt)*100, winp(pt)*100, over(pt,1)*100, median(pb)*100, winp(pb)*100);
        System.out.printf("TOP beat BOT in %d of %d test years.%n", winsTopBeatsBot, evaluated);
        System.out.println("\nStress years (1y-forward crosses a drawdown): 2018(Q4 sell-off), 2021(→2022 bear), 2022.");
        System.out.println("GO for a publishable track record if TOP beats BOT in MOST years incl. stress years,");
        System.out.println("and pooled TOP clearly > BOT. Otherwise the ranker only works in bull markets.");
    }
    double[] pick(List<FitRow> t, Integer[] order, int a, int b) {
        double[] r = new double[b-a]; for (int n=a;n<b;n++) r[n-a]=t.get(order[n]).fwd()[1]; return r;
    }

    /** Point-in-time feature vector at breakout week i (base window W, resistance R). */
    double[] features(List<Bar> b, int i, int W, double R) {
        double volSurge = b.get(i).v() / Math.max(1, medianVol(b, i - W, i - 1));
        double slope = annualizedSlope(b, i - W, i - 1);
        double baseDepth = R / Math.max(1e-9, minLow(b, i - W, i - 1));
        double brkMargin = b.get(i).c() / R - 1;
        double mom26 = i >= 26 ? b.get(i).c() / b.get(i - 26).c() - 1 : 0;
        return new double[]{W, volSurge, slope, baseDepth, brkMargin, mom26};
    }

    /** Fit a transparent linear ranking model on pre-2019 signals, test out-of-sample on 2019+. */
    void fitAndTest(List<double[]> base, List<FitRow> rows) {
        System.out.println("\n================ EMPIRICAL SCORE FIT (out-of-sample) ================");
        LocalDate cut = LocalDate.of(2019, 1, 1);
        List<FitRow> train = new ArrayList<>(), test = new ArrayList<>();
        for (FitRow r : rows) (r.date().isBefore(cut) ? train : test).add(r);
        System.out.printf("Regime-ON breakouts with 1y outcome: %d (train<2019=%d, test>=2019=%d)%n",
                rows.size(), train.size(), test.size());
        if (train.size() < 80 || test.size() < 40) { System.out.println("  insufficient data to fit"); return; }

        int k = FEATURES.length;
        double[] mean = new double[k], std = new double[k];
        for (FitRow r : train) for (int j=0;j<k;j++) mean[j]+=r.x()[j];
        for (int j=0;j<k;j++) mean[j]/=train.size();
        for (FitRow r : train) for (int j=0;j<k;j++) std[j]+=Math.pow(r.x()[j]-mean[j],2);
        for (int j=0;j<k;j++) std[j]=Math.sqrt(std[j]/train.size());
        for (int j=0;j<k;j++) if (std[j]==0) std[j]=1;

        double[][] X = new double[train.size()][k];
        double[] Y = new double[train.size()];
        for (int n=0;n<train.size();n++){ Y[n]=train.get(n).y(); for(int j=0;j<k;j++) X[n][j]=(train.get(n).x()[j]-mean[j])/std[j]; }
        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        ols.newSampleData(Y, X);
        double[] beta = ols.estimateRegressionParameters(); // [intercept, b1..bk]

        System.out.println("Fitted coefficients (standardized → magnitude = importance, sign = direction):");
        System.out.printf("  %-10s %+.4f  (intercept)%n", "base", beta[0]);
        for (int j=0;j<k;j++) System.out.printf("  %-10s %+.4f%n", FEATURES[j], beta[j+1]);
        writeModel(beta, mean, std);

        // score test rows out-of-sample, split into terciles
        double[] scores = new double[test.size()];
        for (int n=0;n<test.size();n++){ double s=beta[0]; for(int j=0;j<k;j++) s+=beta[j+1]*((test.get(n).x()[j]-mean[j])/std[j]); scores[n]=s; }
        Integer[] order = new Integer[test.size()];
        for (int n=0;n<test.size();n++) order[n]=n;
        Arrays.sort(order,(a,c)->Double.compare(scores[c],scores[a])); // desc
        int third = test.size()/3;
        double[] topR = sub(test, order, 0, third), botR = sub(test, order, test.size()-third, test.size());
        double[] bs = col(base,1);
        System.out.println("\nOUT-OF-SAMPLE (2019+) 1y forward returns by fitted-score tercile:");
        System.out.printf("  %-10s N=%d  median=%+.1f%%  win%%=%.0f%%  >100%%=%.0f%%  spreadMed=%+.1f%%%n",
                "TOP 1/3", topR.length, median(topR)*100, winp(topR)*100, over(topR,1)*100, (median(topR)-median(bs))*100);
        System.out.printf("  %-10s N=%d  median=%+.1f%%  win%%=%.0f%%  >100%%=%.0f%%  spreadMed=%+.1f%%%n",
                "BOTTOM 1/3", botR.length, median(botR)*100, winp(botR)*100, over(botR,1)*100, (median(botR)-median(bs))*100);
        System.out.printf("  base-rate 1y median = %+.1f%%%n", median(bs)*100);
        System.out.println("\nGO if TOP tercile clearly beats BOTTOM out-of-sample → the fitted score ranks, ship it as the");
        System.out.println("candidate ranker. Model written to research-output/score_model.txt (productionizable).");
    }
    double[] sub(List<FitRow> t, Integer[] order, int a, int b) {
        double[] r = new double[b-a]; for (int n=a;n<b;n++) r[n-a]=t.get(order[n]).fwd()[1]; return r;
    }
    void writeModel(double[] beta, double[] mean, double[] std) {
        try {
            StringBuilder sb = new StringBuilder("# Long-base-breakout ranking model (standardized linear)\n");
            sb.append("intercept=").append(beta[0]).append('\n');
            for (int j=0;j<FEATURES.length;j++)
                sb.append(FEATURES[j]).append(": coef=").append(beta[j+1])
                  .append(" mean=").append(mean[j]).append(" std=").append(std[j]).append('\n');
            Files.writeString(OUT.resolve("score_model.txt"), sb.toString());
        } catch (Exception e) { /* ignore */ }
    }

    // ---- detection -------------------------------------------------------

    List<Signal> detect(String symbol, List<Bar> b) {
        List<Signal> out = new ArrayList<>();
        int last = -BREAKOUT_COOLDOWN - 1;
        for (int i = MIN_BASE_WEEKS; i < b.size(); i++) {
            if (i - last <= BREAKOUT_COOLDOWN) continue;
            int[] br = testBreakout(b, i);                 // {W} or null
            if (br == null) continue;
            int W = br[0];
            double R = maxHigh(b, i - W, i - 1);
            double volSurge = round1(b.get(i).v() / Math.max(1, medianVol(b, i - W, i - 1)));
            int score = score(W, volSurge, annualizedSlope(b, i - W, i - 1));
            out.add(new Signal(symbol, b.get(i).date(), "BREAKOUT", i, b.get(i).c(), R, W, volSurge, score));
            out.addAll(scanRetests(symbol, b, i, R, W));   // retests tied to this breakout
            last = i;
        }
        return out;
    }

    /** Breakout test at i using only bars <= i. Returns {baseWindow} or null. */
    int[] testBreakout(List<Bar> b, int i) {
        double close = b.get(i).c();
        for (int W : BASE_WINDOWS) {
            if (i < W) continue;
            int s = i - W, e = i - 1;
            double R = maxHigh(b, s, e);
            if (close <= R * (1 + BREAKOUT_BUFFER)) continue;
            if (close < maxClose(b, s, i)) continue;
            double lo = minLow(b, s, e);
            if (lo <= 0 || R / lo > MAX_BASE_RANGE) continue;
            if (Math.abs(annualizedSlope(b, s, e)) > FLAT_SLOPE_MAX) continue;
            if (b.get(i).v() < VOL_MULT * medianVol(b, s, e)) continue;
            double ma30 = sma(b, i, 30), ma30p = sma(b, i - 4, 30), ma40 = sma(b, i, 40);
            if (!(ma30 > ma30p && close > ma40)) continue;
            return new int[]{W};
        }
        return null;
    }

    /** After a breakout at index bi (resistance R), find pullback-and-resume retests. */
    List<Signal> scanRetests(String symbol, List<Bar> b, int bi, double R, int W) {
        List<Signal> out = new ArrayList<>();
        int last = bi;
        int end = Math.min(bi + RETEST_MAX_WEEKS, b.size() - 1);
        for (int j = bi + RETEST_MIN_WEEKS; j <= end && out.size() < MAX_RETESTS; j++) {
            if (j - last < RETEST_COOLDOWN) continue;
            double ma40 = sma(b, j, 40), ma40p = sma(b, j - 4, 40);
            double support = Math.max(R, ma40);            // early: R; later: rising MA40
            if (b.get(j).c() < support * (1 - RETEST_FAIL)) break;   // support lost -> trend broken, abort
            boolean dipped = b.get(j).l() <= support * (1 + RETEST_BAND);
            boolean held = b.get(j).c() >= support * (1 - RETEST_FAIL);
            boolean resuming = b.get(j).c() > b.get(j - 1).c() && b.get(j).c() > b.get(j).o();
            boolean uptrend = ma40 > ma40p;
            if (dipped && held && resuming && uptrend) {
                double volSurge = round1(b.get(j).v() / Math.max(1, medianVol(b, j - 20, j - 1)));
                out.add(new Signal(symbol, b.get(j).date(), "RETEST", j, b.get(j).c(), support, W, volSurge, 0));
                last = j;
            }
        }
        return out;
    }

    int score(int W, double volSurge, double slope) {
        int baseS = (int) Math.round(Math.min(W / 520.0, 1) * 40);
        int volS = (int) Math.round(Math.min(volSurge / 3.0, 1) * 30);
        int tightS = (int) Math.round(Math.max(0, 1 - Math.abs(slope) / FLAT_SLOPE_MAX) * 30);
        return baseS + volS + tightS;
    }

    // ---- helpers (backward-looking) -------------------------------------
    double maxHigh(List<Bar> b,int s,int e){double m=-1;for(int i=s;i<=e;i++)m=Math.max(m,b.get(i).h());return m;}
    double minLow(List<Bar> b,int s,int e){double m=Double.MAX_VALUE;for(int i=s;i<=e;i++)m=Math.min(m,b.get(i).l());return m;}
    double maxClose(List<Bar> b,int s,int e){double m=-1;for(int i=s;i<=e;i++)m=Math.max(m,b.get(i).c());return m;}
    double medianVol(List<Bar> b,int s,int e){double[] v=new double[e-s+1];for(int i=s;i<=e;i++)v[i-s]=b.get(i).v();Arrays.sort(v);return v.length==0?0:v[v.length/2];}
    double sma(List<Bar> b,int i,int p){if(i<p)return b.get(Math.max(0,i)).c();double s=0;for(int j=i-p+1;j<=i;j++)s+=b.get(j).c();return s/p;}
    double annualizedSlope(List<Bar> b,int s,int e){int n=e-s+1;double sx=0,sy=0,sxy=0,sx2=0;for(int k=0;k<n;k++){double x=k,y=b.get(s+k).c();sx+=x;sy+=y;sxy+=x*y;sx2+=x*x;}double d=n*sx2-sx*sx;if(d==0)return 0;double sl=(n*sxy-sx*sy)/d,mn=sy/n;return mn>0?sl*52/mn:0;}
    double[] forward(List<Bar> b,int i){double[] r=new double[HORIZONS.length];double c0=b.get(i).c();for(int h=0;h<HORIZONS.length;h++){int ft=i+HORIZONS[h];r[h]=ft<b.size()?b.get(ft).c()/c0-1.0:Double.NaN;}return r;}
    static double round1(double d){return Math.round(d*10)/10.0;}

    // ---- reporting -------------------------------------------------------
    void report(List<double[]> base, List<double[]> brk, List<double[]> ret) {
        System.out.println("\n================ EDGE REPORT: BREAKOUT vs RETEST ================");
        System.out.printf("%-9s %-8s %8s %8s %7s %7s %9s %8s%n",
                "bucket","horiz","N","median","win%",">100%","spreadMed","win%d");
        for (int h = 0; h < HORIZONS.length; h++) {
            double[] bs = col(base,h);
            row("BASE", HLBL[h], bs, bs);
            row("BREAKOUT", HLBL[h], col(brk,h), bs);
            row("RETEST", HLBL[h], col(ret,h), bs);
            System.out.println();
        }
        System.out.println("Read: RETEST should show HIGHER win% than BREAKOUT (false breakouts filtered),");
        System.out.println("often with similar/smaller raw upside (you enter later). BASE is the benchmark.");
        System.out.println("\nCAVEATS: current index constituents = survivorship bias (no delisted names);");
        System.out.println("overlapping windows; single vendor. Trust spreadMed/win%d, not absolute returns.");
    }
    void row(String bucket, String hl, double[] v, double[] base) {
        if (v.length == 0) { System.out.printf("%-9s %-8s %8d%n", bucket, hl, 0); return; }
        System.out.printf("%-9s %-8s %8d %7.1f%% %6.0f%% %6.0f%% %+8.1f%% %+7.0f%%%n",
                bucket, hl, v.length, median(v)*100, winp(v)*100, over(v,1)*100,
                (median(v)-median(base))*100, (winp(v)-winp(base))*100);
    }
    /** Selectivity: do high-conviction breakouts beat low-conviction ones? Plus regime split. */
    void reportSelectivity(List<double[]> base, List<Obs> obs) {
        System.out.println("\n================ SELECTIVITY: does the conviction SCORE rank returns? ================");
        int[] scores = obs.stream().mapToInt(Obs::score).sorted().toArray();
        if (scores.length < 30) { System.out.println("  too few breakouts"); return; }
        int lo = scores[scores.length/3], hi = scores[2*scores.length/3];
        System.out.printf("Score tertiles: LOW <=%d, HIGH >=%d  (n=%d breakouts)%n", lo, hi, obs.size());
        System.out.printf("%-12s %-6s %8s %8s %7s %7s %9s %8s%n",
                "bucket","horiz","N","median","win%",">100%","spreadMed","win%d");
        for (int h=0; h<HORIZONS.length; h++) {
            double[] bs = col(base,h);
            row("score=LOW",  HLBL[h], colObs(obs, o->o.score()<=lo, h), bs);
            row("score=HIGH", HLBL[h], colObs(obs, o->o.score()>=hi, h), bs);
            System.out.println();
        }
        System.out.println("--- REGIME: breakouts fired with index ABOVE vs BELOW its 40-week MA ---");
        for (int h=0; h<HORIZONS.length; h++) {
            double[] bs = col(base,h);
            row("regime=ON",  HLBL[h], colObs(obs, Obs::regimeOn, h), bs);
            row("regime=OFF", HLBL[h], colObs(obs, o->!o.regimeOn(), h), bs);
            System.out.println();
        }
        System.out.println("Read: if score=HIGH > score=LOW (esp. spreadMed/>100%), the score is worth using →");
        System.out.println("trade only top-conviction breakouts. If regime=ON >> regime=OFF, gate on market regime.");
    }
    double[] colObs(List<Obs> obs, java.util.function.Predicate<Obs> p, int h) {
        return obs.stream().filter(p).mapToDouble(o->o.fwd()[h]).filter(d->!Double.isNaN(d)).toArray();
    }
    double[] col(List<double[]> rows,int h){return rows.stream().mapToDouble(r->r[h]).filter(d->!Double.isNaN(d)).toArray();}
    double median(double[] v){if(v.length==0)return 0;double[] c=v.clone();Arrays.sort(c);int m=c.length/2;return c.length%2==1?c[m]:(c[m-1]+c[m])/2;}
    double winp(double[] v){if(v.length==0)return 0;int w=0;for(double d:v)if(d>0)w++;return (double)w/v.length;}
    double over(double[] v,double t){if(v.length==0)return 0;int w=0;for(double d:v)if(d>t)w++;return (double)w/v.length;}

    void writeSignals(List<Signal> s) throws Exception {
        StringBuilder sb=new StringBuilder("symbol,date,type,price,refLevel,baseWeeks,baseYears,volSurge,score\n");
        s.sort(Comparator.comparing(Signal::date));
        for (Signal x: s) sb.append(String.format(Locale.US,"%s,%s,%s,%.2f,%.2f,%d,%.1f,%.1f,%d%n",
                x.symbol(),x.date(),x.type(),x.price(),x.refLevel(),x.baseWeeks(),x.baseWeeks()/52.0,x.volSurge(),x.score()));
        Files.writeString(OUT.resolve("longbase_signals.csv"), sb.toString());
        System.out.println("Wrote " + OUT.resolve("longbase_signals.csv").toAbsolutePath());
    }

    // ---- universe + data fetch ------------------------------------------
    List<String> loadUniverse() throws Exception {
        Path f = CACHE.resolve("universe.txt");
        if (Files.exists(f)) {
            List<String> u = new ArrayList<>();
            for (String line : Files.readAllLines(f)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) u.add(t);
            }
            if (!u.isEmpty()) return u;
        }
        return Arrays.asList(FALLBACK_UNIVERSE);
    }

    /** Build date -> (index above its 40-week MA) map for a benchmark symbol. */
    TreeMap<LocalDate,Boolean> buildRegime(String symbol) {
        TreeMap<LocalDate,Boolean> map = new TreeMap<>();
        try {
            List<Bar> b = fetchWeekly(symbol);
            for (int i = 40; i < b.size(); i++) map.put(b.get(i).date(), b.get(i).c() >= sma(b, i, 40));
        } catch (Exception e) { System.out.println("  regime fetch failed for " + symbol + ": " + e.getMessage()); }
        return map;
    }
    boolean regimeOn(TreeMap<LocalDate,Boolean> regime, LocalDate d) {
        if (regime.isEmpty()) return true;                 // unknown -> don't filter
        Map.Entry<LocalDate,Boolean> e = regime.floorEntry(d);
        return e == null ? true : e.getValue();
    }

    List<Bar> fetchWeekly(String symbol) throws Exception {
        Path cache = CACHE.resolve(symbol.replace('.', '_') + "_1wk.json");
        String body;
        if (Files.exists(cache) && Files.size(cache) > 200) body = Files.readString(cache);
        else {
            long now = java.time.Instant.now().getEpochSecond();
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                    + "?period1=0&period2=" + now + "&interval=1wk&events=div%2Csplit&includeAdjustedClose=true";
            body = null;
            for (int attempt = 0; attempt < 3 && body == null; attempt++) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(Duration.ofSeconds(30)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) body = resp.body();
                else if (resp.statusCode() == 429) Thread.sleep(2000);   // backoff
                else throw new RuntimeException("HTTP " + resp.statusCode());
            }
            if (body == null) throw new RuntimeException("rate-limited");
            Files.writeString(cache, body);
            Thread.sleep(120);
        }
        JsonNode result = mapper.readTree(body).path("chart").path("result").get(0);
        if (result == null) throw new RuntimeException("no result");
        JsonNode ts = result.path("timestamp");
        JsonNode q = result.path("indicators").path("quote").get(0);
        JsonNode adjN = result.path("indicators").path("adjclose").get(0);
        if (ts.isMissingNode() || q == null || adjN == null) throw new RuntimeException("empty");
        JsonNode adj = adjN.path("adjclose");
        JsonNode o=q.path("open"),h=q.path("high"),l=q.path("low"),c=q.path("close"),v=q.path("volume");
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            if (c.get(i).isNull()||o.get(i).isNull()||adj.get(i).isNull()) continue;
            double cc=c.get(i).asDouble(); if (cc<=0) continue;
            double f=adj.get(i).asDouble()/cc;
            long vol=v.get(i).isNull()?0:v.get(i).asLong();
            bars.add(new Bar(LocalDate.ofInstant(java.time.Instant.ofEpochSecond(ts.get(i).asLong()),ZoneOffset.UTC),
                    o.get(i).asDouble()*f,h.get(i).asDouble()*f,l.get(i).asDouble()*f,cc*f,vol));
        }
        bars.sort(Comparator.comparing(Bar::date));
        return bars;
    }
}
