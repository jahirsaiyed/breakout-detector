package com.breakoutdetector.service;

import com.breakoutdetector.model.BreakoutSignal;
import com.breakoutdetector.model.StockData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StaircasePatternService {

    private static final int STAIRCASE_LOOKBACK = 100; // Days to look back for staircase pattern
    private static final int MIN_BREAKOUTS_FOR_STAIRCASE = 3; // Minimum breakouts to form staircase
    private static final double MIN_BREAKOUT_PERCENT = 5.0; // Minimum 5% breakout
    private static final double MAX_PULLBACK_PERCENT = 15.0; // Maximum 15% pullback between breakouts

    public static class StaircasePattern {
        private String symbol;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int totalBreakouts;
        private int totalBreakdowns;
        private List<BreakoutSignal> breakoutSignals;
        private double totalPriceIncrease;
        private double averageBreakoutSize;
        private String patternStrength; // "STRONG", "MODERATE", "WEAK"
        private String analysis;

        // Constructors
        public StaircasePattern() {}

        public StaircasePattern(String symbol, LocalDateTime startDate, LocalDateTime endDate) {
            this.symbol = symbol;
            this.startDate = startDate;
            this.endDate = endDate;
            this.breakoutSignals = new ArrayList<>();
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public int getTotalBreakouts() { return totalBreakouts; }
        public void setTotalBreakouts(int totalBreakouts) { this.totalBreakouts = totalBreakouts; }
        public int getTotalBreakdowns() { return totalBreakdowns; }
        public void setTotalBreakdowns(int totalBreakdowns) { this.totalBreakdowns = totalBreakdowns; }
        public List<BreakoutSignal> getBreakoutSignals() { return breakoutSignals; }
        public void setBreakoutSignals(List<BreakoutSignal> breakoutSignals) { this.breakoutSignals = breakoutSignals; }
        public double getTotalPriceIncrease() { return totalPriceIncrease; }
        public void setTotalPriceIncrease(double totalPriceIncrease) { this.totalPriceIncrease = totalPriceIncrease; }
        public double getAverageBreakoutSize() { return averageBreakoutSize; }
        public void setAverageBreakoutSize(double averageBreakoutSize) { this.averageBreakoutSize = averageBreakoutSize; }
        public String getPatternStrength() { return patternStrength; }
        public void setPatternStrength(String patternStrength) { this.patternStrength = patternStrength; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }

    public StaircasePattern detectStaircasePattern(List<StockData> stockDataList, List<BreakoutSignal> signals) {
        if (stockDataList.size() < STAIRCASE_LOOKBACK) {
            return null; // Need sufficient data
        }

        // Sort signals by date
        signals.sort(Comparator.comparing(BreakoutSignal::getSignalDate));
        
        // Filter signals for the lookback period
        LocalDateTime cutoffDate = stockDataList.get(stockDataList.size() - 1).getTimestamp().minusDays(STAIRCASE_LOOKBACK);
        List<BreakoutSignal> recentSignals = signals.stream()
            .filter(signal -> signal.getSignalDate().isAfter(cutoffDate))
            .collect(Collectors.toList());

        if (recentSignals.size() < MIN_BREAKOUTS_FOR_STAIRCASE) {
            return null; // Not enough breakouts
        }

        // Analyze for staircase pattern
        StaircasePattern pattern = analyzeStaircasePattern(stockDataList, recentSignals);
        
        if (pattern != null && isValidStaircasePattern(pattern)) {
            return pattern;
        }

        return null;
    }

    private StaircasePattern analyzeStaircasePattern(List<StockData> stockDataList, List<BreakoutSignal> signals) {
        StaircasePattern pattern = new StaircasePattern(
            stockDataList.get(0).getSymbol(),
            signals.get(0).getSignalDate(),
            signals.get(signals.size() - 1).getSignalDate()
        );

        // Categorize signals
        List<BreakoutSignal> breakoutSignals = signals.stream()
            .filter(signal -> signal.getSignalType().contains("BREAKOUT") && !signal.getSignalType().contains("BREAKDOWN"))
            .collect(Collectors.toList());

        List<BreakoutSignal> breakdownSignals = signals.stream()
            .filter(signal -> signal.getSignalType().contains("BREAKDOWN"))
            .collect(Collectors.toList());

        pattern.setTotalBreakouts(breakoutSignals.size());
        pattern.setTotalBreakdowns(breakdownSignals.size());
        pattern.setBreakoutSignals(breakoutSignals);

        // Calculate price increases
        if (breakoutSignals.size() >= 2) {
            double startPrice = breakoutSignals.get(0).getBreakoutPrice().doubleValue();
            double endPrice = breakoutSignals.get(breakoutSignals.size() - 1).getBreakoutPrice().doubleValue();
            pattern.setTotalPriceIncrease(((endPrice - startPrice) / startPrice) * 100);
            
            double totalBreakoutSize = 0;
            for (BreakoutSignal signal : breakoutSignals) {
                totalBreakoutSize += signal.getBreakoutPrice().doubleValue();
            }
            pattern.setAverageBreakoutSize(totalBreakoutSize / breakoutSignals.size());
        }

        // Determine pattern strength
        pattern.setPatternStrength(determinePatternStrength(pattern));
        
        // Generate analysis
        pattern.setAnalysis(generateStaircaseAnalysis(pattern, stockDataList));

        return pattern;
    }

    private boolean isValidStaircasePattern(StaircasePattern pattern) {
        // Must have at least 3 breakouts
        if (pattern.getTotalBreakouts() < MIN_BREAKOUTS_FOR_STAIRCASE) {
            return false;
        }

        // Check if breakouts are progressive (each higher than the previous)
        List<BreakoutSignal> breakouts = pattern.getBreakoutSignals();
        for (int i = 1; i < breakouts.size(); i++) {
            double currentPrice = breakouts.get(i).getBreakoutPrice().doubleValue();
            double previousPrice = breakouts.get(i - 1).getBreakoutPrice().doubleValue();
            
            if (currentPrice <= previousPrice) {
                return false; // Not a proper staircase
            }
        }

        // Check for reasonable pullbacks between breakouts
        for (int i = 1; i < breakouts.size(); i++) {
            double currentBreakout = breakouts.get(i).getBreakoutPrice().doubleValue();
            double previousBreakout = breakouts.get(i - 1).getBreakoutPrice().doubleValue();
            
            double pullback = ((previousBreakout - currentBreakout) / previousBreakout) * 100;
            if (pullback > MAX_PULLBACK_PERCENT) {
                return false; // Too much pullback
            }
        }

        return true;
    }

    private String determinePatternStrength(StaircasePattern pattern) {
        int breakouts = pattern.getTotalBreakouts();
        double priceIncrease = pattern.getTotalPriceIncrease();
        int breakdowns = pattern.getTotalBreakdowns();

        if (breakouts >= 5 && priceIncrease > 50 && breakdowns <= 2) {
            return "STRONG";
        } else if (breakouts >= 3 && priceIncrease > 25 && breakdowns <= 3) {
            return "MODERATE";
        } else {
            return "WEAK";
        }
    }

    private String generateStaircaseAnalysis(StaircasePattern pattern, List<StockData> stockDataList) {
        StringBuilder analysis = new StringBuilder();
        
        analysis.append("Staircase Pattern Detected: ");
        analysis.append(pattern.getTotalBreakouts()).append(" breakouts, ");
        analysis.append(pattern.getTotalBreakdowns()).append(" breakdowns. ");
        analysis.append("Total price increase: ").append(String.format("%.2f", pattern.getTotalPriceIncrease())).append("%. ");
        analysis.append("Pattern strength: ").append(pattern.getPatternStrength()).append(". ");
        
        if (pattern.getTotalBreakouts() >= 3) {
            analysis.append("This stock shows early-stage multibagger characteristics with progressive breakouts.");
        }
        
        return analysis.toString();
    }

    public Map<String, Object> getStaircaseDashboard(List<StockData> stockDataList, List<BreakoutSignal> signals) {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Detect staircase pattern
        StaircasePattern pattern = detectStaircasePattern(stockDataList, signals);
        
        if (pattern != null) {
            dashboard.put("hasStaircasePattern", true);
            dashboard.put("pattern", pattern);
            dashboard.put("patternStrength", pattern.getPatternStrength());
            dashboard.put("totalBreakouts", pattern.getTotalBreakouts());
            dashboard.put("totalBreakdowns", pattern.getTotalBreakdowns());
            dashboard.put("totalPriceIncrease", pattern.getTotalPriceIncrease());
            dashboard.put("analysis", pattern.getAnalysis());
        } else {
            dashboard.put("hasStaircasePattern", false);
            dashboard.put("analysis", "No clear staircase pattern detected. Need more breakouts or better pattern formation.");
        }

        // Calculate breakout statistics
        Map<String, Long> signalTypeCounts = signals.stream()
            .collect(Collectors.groupingBy(BreakoutSignal::getSignalType, Collectors.counting()));
        
        dashboard.put("signalTypeCounts", signalTypeCounts);
        dashboard.put("totalSignals", signals.size());
        
        // Calculate recent activity (last 30 days)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long recentSignals = signals.stream()
            .filter(signal -> signal.getSignalDate().isAfter(thirtyDaysAgo))
            .count();
        
        dashboard.put("recentSignals", recentSignals);
        
        return dashboard;
    }

    public List<StaircasePattern> findStaircasePatterns(List<String> symbols, 
                                                       Map<String, List<StockData>> stockDataMap,
                                                       Map<String, List<BreakoutSignal>> signalsMap) {
        List<StaircasePattern> patterns = new ArrayList<>();
        
        for (String symbol : symbols) {
            List<StockData> stockData = stockDataMap.get(symbol);
            List<BreakoutSignal> signals = signalsMap.get(symbol);
            
            if (stockData != null && signals != null) {
                StaircasePattern pattern = detectStaircasePattern(stockData, signals);
                if (pattern != null) {
                    patterns.add(pattern);
                }
            }
        }
        
        // Sort by pattern strength and total breakouts
        patterns.sort((p1, p2) -> {
            int strength1 = getStrengthValue(p1.getPatternStrength());
            int strength2 = getStrengthValue(p2.getPatternStrength());
            
            if (strength1 != strength2) {
                return Integer.compare(strength2, strength1); // Strongest first
            }
            
            return Integer.compare(p2.getTotalBreakouts(), p1.getTotalBreakouts());
        });
        
        return patterns;
    }

    private int getStrengthValue(String strength) {
        switch (strength) {
            case "STRONG": return 3;
            case "MODERATE": return 2;
            case "WEAK": return 1;
            default: return 0;
        }
    }

    public Map<String, Object> getMultibaggerScore(String symbol, List<StockData> stockDataList, List<BreakoutSignal> signals) {
        Map<String, Object> score = new HashMap<>();
        
        StaircasePattern pattern = detectStaircasePattern(stockDataList, signals);
        
        int multibaggerScore = 0;
        String scoreLevel = "LOW";
        
        if (pattern != null) {
            // Calculate multibagger score based on various factors
            multibaggerScore += pattern.getTotalBreakouts() * 10;
            multibaggerScore += (int) (pattern.getTotalPriceIncrease() / 10);
            multibaggerScore -= pattern.getTotalBreakdowns() * 5;
            
            if ("STRONG".equals(pattern.getPatternStrength())) {
                multibaggerScore += 30;
            } else if ("MODERATE".equals(pattern.getPatternStrength())) {
                multibaggerScore += 15;
            }
        }
        
        // Additional factors
        long volumeBreakouts = signals.stream()
            .filter(s -> s.getSignalType().contains("VOLUME"))
            .count();
        multibaggerScore += volumeBreakouts * 5;
        
        // Determine score level
        if (multibaggerScore >= 80) {
            scoreLevel = "VERY_HIGH";
        } else if (multibaggerScore >= 60) {
            scoreLevel = "HIGH";
        } else if (multibaggerScore >= 40) {
            scoreLevel = "MEDIUM";
        } else if (multibaggerScore >= 20) {
            scoreLevel = "LOW";
        } else {
            scoreLevel = "VERY_LOW";
        }
        
        score.put("symbol", symbol);
        score.put("multibaggerScore", multibaggerScore);
        score.put("scoreLevel", scoreLevel);
        score.put("hasStaircasePattern", pattern != null);
        score.put("pattern", pattern);
        
        return score;
    }
} 