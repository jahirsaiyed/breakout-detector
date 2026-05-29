package com.breakoutdetector.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "breakout_signals")
public class BreakoutSignal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDateTime signalDate;
    
    @Column(nullable = false)
    private String signalType; // VOLUME_BREAKOUT, PRICE_BREAKOUT, RSI_DIVERGENCE, etc.
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal breakoutPrice;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal resistanceLevel;
    
    @Column(nullable = false)
    private Long volume;
    
    @Column(nullable = false)
    private Long averageVolume;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal volumeRatio;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal rsi;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal macd;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal adx;
    
    @Column(nullable = false)
    private Boolean confirmed;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal targetPrice;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal stopLoss;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal riskRewardRatio;
    
    @Column(length = 1000)
    private String analysis;
    
    // Constructors
    public BreakoutSignal() {}
    
    public BreakoutSignal(String symbol, LocalDateTime signalDate, String signalType, 
                         BigDecimal breakoutPrice, BigDecimal resistanceLevel, 
                         Long volume, Long averageVolume) {
        this.symbol = symbol;
        this.signalDate = signalDate;
        this.signalType = signalType;
        this.breakoutPrice = breakoutPrice;
        this.resistanceLevel = resistanceLevel;
        this.volume = volume;
        this.averageVolume = averageVolume;
        this.confirmed = false;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public LocalDateTime getSignalDate() {
        return signalDate;
    }
    
    public void setSignalDate(LocalDateTime signalDate) {
        this.signalDate = signalDate;
    }
    
    public String getSignalType() {
        return signalType;
    }
    
    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }
    
    public BigDecimal getBreakoutPrice() {
        return breakoutPrice;
    }
    
    public void setBreakoutPrice(BigDecimal breakoutPrice) {
        this.breakoutPrice = breakoutPrice;
    }
    
    public BigDecimal getResistanceLevel() {
        return resistanceLevel;
    }
    
    public void setResistanceLevel(BigDecimal resistanceLevel) {
        this.resistanceLevel = resistanceLevel;
    }
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
    
    public Long getAverageVolume() {
        return averageVolume;
    }
    
    public void setAverageVolume(Long averageVolume) {
        this.averageVolume = averageVolume;
    }
    
    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }
    
    public void setVolumeRatio(BigDecimal volumeRatio) {
        this.volumeRatio = volumeRatio;
    }
    
    public BigDecimal getRsi() {
        return rsi;
    }
    
    public void setRsi(BigDecimal rsi) {
        this.rsi = rsi;
    }
    
    public BigDecimal getMacd() {
        return macd;
    }
    
    public void setMacd(BigDecimal macd) {
        this.macd = macd;
    }
    
    public BigDecimal getAdx() {
        return adx;
    }
    
    public void setAdx(BigDecimal adx) {
        this.adx = adx;
    }
    
    public Boolean getConfirmed() {
        return confirmed;
    }
    
    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }
    
    public BigDecimal getTargetPrice() {
        return targetPrice;
    }
    
    public void setTargetPrice(BigDecimal targetPrice) {
        this.targetPrice = targetPrice;
    }
    
    public BigDecimal getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(BigDecimal stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public BigDecimal getRiskRewardRatio() {
        return riskRewardRatio;
    }
    
    public void setRiskRewardRatio(BigDecimal riskRewardRatio) {
        this.riskRewardRatio = riskRewardRatio;
    }
    
    public String getAnalysis() {
        return analysis;
    }
    
    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }
} 