package com.breakoutdetector.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * A long-base-breakout candidate surfaced by a scan. The snapshot at scan time is the
 * basis of the live track record; forward-return fields are filled in later by evaluation.
 */
@Entity
@Table(name = "candidates", indexes = {
        @Index(name = "idx_scan_date", columnList = "scanDate"),
        @Index(name = "idx_symbol", columnList = "symbol")
})
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate scanDate;          // when this scan ran (track-record anchor)

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String market;               // "US" or "IN"

    @Column(nullable = false)
    private LocalDate signalDate;        // week the breakout fired

    @Column(nullable = false)
    private double price;                // adjusted close at signal

    @Column(nullable = false)
    private double resistanceLevel;      // multi-year resistance broken

    @Column(nullable = false)
    private int baseWeeks;

    @Column(nullable = false)
    private double volSurge;

    @Column(nullable = false)
    private double breakoutMargin;

    @Column(nullable = false)
    private double momentum26w;

    @Column(nullable = false)
    private double rankScore;            // fitted-model score

    @Column(nullable = false)
    private double percentileRank;       // 0..1 within this scan's breakouts

    @Column(nullable = false)
    private boolean regimeOn;

    // ---- forward performance (null until evaluated) ----
    private Double returnSinceSignal;    // updated by an evaluation job
    private LocalDate lastEvaluated;

    public Candidate() {}

    public Long getId() { return id; }
    public LocalDate getScanDate() { return scanDate; }
    public void setScanDate(LocalDate scanDate) { this.scanDate = scanDate; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public LocalDate getSignalDate() { return signalDate; }
    public void setSignalDate(LocalDate signalDate) { this.signalDate = signalDate; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getResistanceLevel() { return resistanceLevel; }
    public void setResistanceLevel(double resistanceLevel) { this.resistanceLevel = resistanceLevel; }
    public int getBaseWeeks() { return baseWeeks; }
    public void setBaseWeeks(int baseWeeks) { this.baseWeeks = baseWeeks; }
    public double getVolSurge() { return volSurge; }
    public void setVolSurge(double volSurge) { this.volSurge = volSurge; }
    public double getBreakoutMargin() { return breakoutMargin; }
    public void setBreakoutMargin(double breakoutMargin) { this.breakoutMargin = breakoutMargin; }
    public double getMomentum26w() { return momentum26w; }
    public void setMomentum26w(double momentum26w) { this.momentum26w = momentum26w; }
    public double getRankScore() { return rankScore; }
    public void setRankScore(double rankScore) { this.rankScore = rankScore; }
    public double getPercentileRank() { return percentileRank; }
    public void setPercentileRank(double percentileRank) { this.percentileRank = percentileRank; }
    public boolean isRegimeOn() { return regimeOn; }
    public void setRegimeOn(boolean regimeOn) { this.regimeOn = regimeOn; }
    public Double getReturnSinceSignal() { return returnSinceSignal; }
    public void setReturnSinceSignal(Double returnSinceSignal) { this.returnSinceSignal = returnSinceSignal; }
    public LocalDate getLastEvaluated() { return lastEvaluated; }
    public void setLastEvaluated(LocalDate lastEvaluated) { this.lastEvaluated = lastEvaluated; }
}
