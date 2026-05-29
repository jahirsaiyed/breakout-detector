package com.breakoutdetector.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_data")
public class StockData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal open;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal high;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal low;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal close;
    
    @Column(nullable = false)
    private Long volume;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal change;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal changePercent;
    
    // Constructors
    public StockData() {}
    
    public StockData(String symbol, LocalDateTime timestamp, BigDecimal open, BigDecimal high, 
                    BigDecimal low, BigDecimal close, Long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
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
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public BigDecimal getOpen() {
        return open;
    }
    
    public void setOpen(BigDecimal open) {
        this.open = open;
    }
    
    public BigDecimal getHigh() {
        return high;
    }
    
    public void setHigh(BigDecimal high) {
        this.high = high;
    }
    
    public BigDecimal getLow() {
        return low;
    }
    
    public void setLow(BigDecimal low) {
        this.low = low;
    }
    
    public BigDecimal getClose() {
        return close;
    }
    
    public void setClose(BigDecimal close) {
        this.close = close;
    }
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
    
    public BigDecimal getChange() {
        return change;
    }
    
    public void setChange(BigDecimal change) {
        this.change = change;
    }
    
    public BigDecimal getChangePercent() {
        return changePercent;
    }
    
    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }
} 