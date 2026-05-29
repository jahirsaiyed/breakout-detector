package com.breakoutdetector.service;

import com.breakoutdetector.model.WeeklyBar;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The validated flagship signal: multi-year base breakout, ranked by an empirically-fitted
 * 6-factor model (out-of-sample walk-forward: top tercile +31% vs bottom +16% median 1y,
 * beat bottom in 9 of 11 years incl. the 2022 bear).
 *
 * IMPORTANT — feature parity: the MODEL constants below were fitted with the exact feature
 * computation in {@link #features}, including the volSurge quirk (raw volume leaks in when the
 * base's median volume is 0). Do NOT "clean" a feature without refitting the model, or the
 * validated score becomes invalid. Refitting cleaner features is tracked tech-debt.
 */
@Service
public class LongBaseBreakoutService {

    // ---- detector params (match research/LongBaseBreakoutRunner) ----
    static final int MIN_BASE_WEEKS = 100;
    static final int[] BASE_WINDOWS = {520, 400, 300, 200, 150, 100};
    static final double BREAKOUT_BUFFER = 0.05;
    static final double VOL_MULT = 2.0;
    static final double FLAT_SLOPE_MAX = 0.15;
    static final double MAX_BASE_RANGE = 6.0;
    static final int COOLDOWN_WEEKS = 52;

    // ---- fitted model (research-output/score_model.txt); order = features() ----
    static final double INTERCEPT = 0.15661817067241773;
    static final double[] COEF = {-0.013725531952367325, -0.010364588608985982, 0.010953500924354855,
            -0.004350970020355365, -0.06358395323387736, 0.055434050873971435};
    static final double[] MEAN = {260.1099706744868, 179487.53922805068, 0.06609461970121473,
            3.0599947336777236, 0.12967787154460161, 0.5859668067914644};
    static final double[] STD = {156.9151000071788, 4046799.816070551, 0.06379667239567466,
            1.2291291873486128, 1.1911318330887823, 1.6373141078003568};

    public record ScoredSignal(java.time.LocalDate date, int idx, double price, double resistance,
                               int baseWeeks, double volSurge, double breakoutMargin,
                               double momentum26w, double rankScore) {}

    /** All long-base breakouts in the series (point-in-time), each scored by the fitted model. */
    public List<ScoredSignal> detect(List<WeeklyBar> b) {
        List<ScoredSignal> out = new ArrayList<>();
        int last = -COOLDOWN_WEEKS - 1;
        for (int i = MIN_BASE_WEEKS; i < b.size(); i++) {
            if (i - last <= COOLDOWN_WEEKS) continue;
            int W = testBreakout(b, i);
            if (W < 0) continue;
            double R = maxHigh(b, i - W, i - 1);
            double[] x = features(b, i, W, R);
            out.add(new ScoredSignal(b.get(i).date(), i, b.get(i).close(), R, W,
                    x[1], x[4], x[5], score(x)));
            last = i;
        }
        return out;
    }

    /** Breakout test at i using only bars <= i. Returns base window W, or -1. */
    int testBreakout(List<WeeklyBar> b, int i) {
        double close = b.get(i).close();
        for (int W : BASE_WINDOWS) {
            if (i < W) continue;
            int s = i - W, e = i - 1;
            double resistance = maxHigh(b, s, e);
            if (close <= resistance * (1 + BREAKOUT_BUFFER)) continue;
            if (close < maxClose(b, s, i)) continue;
            double minLow = minLow(b, s, e);
            if (minLow <= 0 || resistance / minLow > MAX_BASE_RANGE) continue;
            if (Math.abs(annualizedSlope(b, s, e)) > FLAT_SLOPE_MAX) continue;
            double medVol = medianVol(b, s, e);
            double volSurge = medVol > 0 ? b.get(i).volume() / medVol : 0;
            if (volSurge < VOL_MULT) continue;
            double ma30 = sma(b, i, 30), ma30prev = sma(b, i - 4, 30), ma40 = sma(b, i, 40);
            if (!(ma30 > ma30prev && close > ma40)) continue;
            return W;
        }
        return -1;
    }

    /** Point-in-time feature vector — MUST match the fitting code (see class note). */
    double[] features(List<WeeklyBar> b, int i, int W, double R) {
        double volSurge = b.get(i).volume() / Math.max(1, medianVol(b, i - W, i - 1));
        double slope = annualizedSlope(b, i - W, i - 1);
        double baseDepth = R / Math.max(1e-9, minLow(b, i - W, i - 1));
        double brkMargin = b.get(i).close() / R - 1;
        double mom26 = i >= 26 ? b.get(i).close() / b.get(i - 26).close() - 1 : 0;
        return new double[]{W, volSurge, slope, baseDepth, brkMargin, mom26};
    }

    double score(double[] x) {
        double s = INTERCEPT;
        for (int j = 0; j < COEF.length; j++) s += COEF[j] * ((x[j] - MEAN[j]) / STD[j]);
        return s;
    }

    // ---- backward-looking helpers ----
    double maxHigh(List<WeeklyBar> b, int s, int e) { double m = -1; for (int i = s; i <= e; i++) m = Math.max(m, b.get(i).high()); return m; }
    double minLow(List<WeeklyBar> b, int s, int e) { double m = Double.MAX_VALUE; for (int i = s; i <= e; i++) m = Math.min(m, b.get(i).low()); return m; }
    double maxClose(List<WeeklyBar> b, int s, int e) { double m = -1; for (int i = s; i <= e; i++) m = Math.max(m, b.get(i).close()); return m; }
    double medianVol(List<WeeklyBar> b, int s, int e) {
        double[] v = new double[e - s + 1];
        for (int i = s; i <= e; i++) v[i - s] = b.get(i).volume();
        Arrays.sort(v);
        return v.length == 0 ? 0 : v[v.length / 2];
    }
    double sma(List<WeeklyBar> b, int i, int p) {
        if (i < p) return b.get(Math.max(0, i)).close();
        double sum = 0; for (int j = i - p + 1; j <= i; j++) sum += b.get(j).close();
        return sum / p;
    }
    double annualizedSlope(List<WeeklyBar> b, int s, int e) {
        int n = e - s + 1; double sx = 0, sy = 0, sxy = 0, sx2 = 0;
        for (int k = 0; k < n; k++) { double x = k, y = b.get(s + k).close(); sx += x; sy += y; sxy += x * y; sx2 += x * x; }
        double denom = n * sx2 - sx * sx; if (denom == 0) return 0;
        double slope = (n * sxy - sx * sy) / denom, mean = sy / n;
        return mean > 0 ? slope * 52 / mean : 0;
    }
}
