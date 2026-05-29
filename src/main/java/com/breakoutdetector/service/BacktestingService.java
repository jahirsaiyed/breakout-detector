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
public class BacktestingService {

    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("100000"); // $100,000
    private static final BigDecimal POSITION_SIZE = new BigDecimal("0.1"); // 10% per trade
    private static final BigDecimal STOP_LOSS_PERCENT = new BigDecimal("0.05"); // 5% stop loss
    private static final BigDecimal TAKE_PROFIT_PERCENT = new BigDecimal("0.20"); // 20% take profit

    public BacktestResult backtestStrategy(List<StockData> stockDataList, List<BreakoutSignal> signals) {
        BacktestResult result = new BacktestResult();
        result.setInitialCapital(INITIAL_CAPITAL);
        result.setStartDate(stockDataList.get(0).getTimestamp());
        result.setEndDate(stockDataList.get(stockDataList.size() - 1).getTimestamp());

        BigDecimal currentCapital = INITIAL_CAPITAL;
        List<Trade> trades = new ArrayList<>();
        Map<String, Position> openPositions = new HashMap<>();

        // Sort signals by date
        signals.sort(Comparator.comparing(BreakoutSignal::getSignalDate));

        for (BreakoutSignal signal : signals) {
            // Find the stock data for this signal
            StockData signalData = findStockDataAtDate(stockDataList, signal.getSignalDate());
            if (signalData == null) continue;

            // Check if we should enter a position
            if (shouldEnterPosition(signal, openPositions)) {
                Position position = enterPosition(signal, signalData, currentCapital);
                openPositions.put(signal.getSymbol(), position);
            }

            // Check existing positions for exit conditions
            List<String> positionsToClose = new ArrayList<>();
            for (Map.Entry<String, Position> entry : openPositions.entrySet()) {
                Position position = entry.getValue();
                StockData currentData = findStockDataAtDate(stockDataList, signal.getSignalDate());
                
                if (currentData != null && shouldExitPosition(position, currentData)) {
                    Trade trade = closePosition(position, currentData);
                    trades.add(trade);
                    currentCapital = currentCapital.add(trade.getProfitLoss());
                    positionsToClose.add(entry.getKey());
                }
            }

            // Close positions
            for (String symbol : positionsToClose) {
                openPositions.remove(symbol);
            }
        }

        // Close any remaining positions at the end
        StockData lastData = stockDataList.get(stockDataList.size() - 1);
        for (Position position : openPositions.values()) {
            Trade trade = closePosition(position, lastData);
            trades.add(trade);
            currentCapital = currentCapital.add(trade.getProfitLoss());
        }

        // Calculate results
        result.setFinalCapital(currentCapital);
        result.setTotalReturn(currentCapital.subtract(INITIAL_CAPITAL));
        result.setTotalReturnPercent(result.getTotalReturn()
            .divide(INITIAL_CAPITAL, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100")));
        result.setTrades(trades);
        result.setTotalTrades(trades.size());
        result.setWinningTrades((int) trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
            .count());
        result.setLosingTrades((int) trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0)
            .count());

        if (result.getTotalTrades() > 0) {
            result.setWinRate(new BigDecimal(result.getWinningTrades())
                .divide(new BigDecimal(result.getTotalTrades()), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")));
        }

        // Calculate additional metrics
        calculateAdditionalMetrics(result, trades);

        return result;
    }

    private boolean shouldEnterPosition(BreakoutSignal signal, Map<String, Position> openPositions) {
        // Don't enter if we already have a position in this stock
        if (openPositions.containsKey(signal.getSymbol())) {
            return false;
        }

        // Check signal strength
        BigDecimal volumeRatio = signal.getVolumeRatio();
        if (volumeRatio == null || volumeRatio.compareTo(new BigDecimal("1.5")) < 0) {
            return false;
        }

        // Check RSI if available
        if (signal.getRsi() != null) {
            BigDecimal rsi = signal.getRsi();
            if (rsi.compareTo(new BigDecimal("30")) < 0 || rsi.compareTo(new BigDecimal("70")) > 0) {
                return false;
            }
        }

        return true;
    }

    private Position enterPosition(BreakoutSignal signal, StockData data, BigDecimal currentCapital) {
        BigDecimal positionValue = currentCapital.multiply(POSITION_SIZE);
        BigDecimal shares = positionValue.divide(data.getClose(), 0, RoundingMode.DOWN);
        
        Position position = new Position();
        position.setSymbol(signal.getSymbol());
        position.setEntryDate(signal.getSignalDate());
        position.setEntryPrice(data.getClose());
        position.setShares(shares);
        position.setEntryValue(positionValue);
        position.setStopLoss(data.getClose().multiply(BigDecimal.ONE.subtract(STOP_LOSS_PERCENT)));
        position.setTakeProfit(data.getClose().multiply(BigDecimal.ONE.add(TAKE_PROFIT_PERCENT)));
        position.setSignalType(signal.getSignalType());
        
        return position;
    }

    private boolean shouldExitPosition(Position position, StockData currentData) {
        BigDecimal currentPrice = currentData.getClose();
        
        // Stop loss hit
        if (currentPrice.compareTo(position.getStopLoss()) <= 0) {
            return true;
        }
        
        // Take profit hit
        if (currentPrice.compareTo(position.getTakeProfit()) >= 0) {
            return true;
        }
        
        // Time-based exit (hold for maximum 30 days)
        long daysHeld = java.time.Duration.between(position.getEntryDate(), currentData.getTimestamp()).toDays();
        if (daysHeld >= 30) {
            return true;
        }
        
        return false;
    }

    private Trade closePosition(Position position, StockData currentData) {
        BigDecimal exitValue = position.getShares().multiply(currentData.getClose());
        BigDecimal profitLoss = exitValue.subtract(position.getEntryValue());
        
        Trade trade = new Trade();
        trade.setSymbol(position.getSymbol());
        trade.setEntryDate(position.getEntryDate());
        trade.setExitDate(currentData.getTimestamp());
        trade.setEntryPrice(position.getEntryPrice());
        trade.setExitPrice(currentData.getClose());
        trade.setShares(position.getShares());
        trade.setProfitLoss(profitLoss);
        trade.setReturnPercent(profitLoss.divide(position.getEntryValue(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100")));
        trade.setSignalType(position.getSignalType());
        
        return trade;
    }

    private StockData findStockDataAtDate(List<StockData> stockDataList, LocalDateTime targetDate) {
        return stockDataList.stream()
            .filter(data -> data.getTimestamp().equals(targetDate))
            .findFirst()
            .orElse(null);
    }

    private void calculateAdditionalMetrics(BacktestResult result, List<Trade> trades) {
        if (trades.isEmpty()) return;

        // Calculate average trade return
        BigDecimal totalReturn = trades.stream()
            .map(Trade::getProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.setAverageTradeReturn(totalReturn.divide(new BigDecimal(trades.size()), 4, RoundingMode.HALF_UP));

        // Calculate maximum drawdown
        BigDecimal maxDrawdown = calculateMaxDrawdown(trades);
        result.setMaxDrawdown(maxDrawdown);

        // Calculate Sharpe ratio (simplified)
        BigDecimal sharpeRatio = calculateSharpeRatio(trades);
        result.setSharpeRatio(sharpeRatio);

        // Calculate profit factor
        BigDecimal profitFactor = calculateProfitFactor(trades);
        result.setProfitFactor(profitFactor);

        // Calculate average winning and losing trades
        List<Trade> winningTrades = trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
            .collect(Collectors.toList());
        
        List<Trade> losingTrades = trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.toList());

        if (!winningTrades.isEmpty()) {
            BigDecimal avgWin = winningTrades.stream()
                .map(Trade::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(winningTrades.size()), 2, RoundingMode.HALF_UP);
            result.setAverageWin(avgWin);
        }

        if (!losingTrades.isEmpty()) {
            BigDecimal avgLoss = losingTrades.stream()
                .map(Trade::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(losingTrades.size()), 2, RoundingMode.HALF_UP);
            result.setAverageLoss(avgLoss);
        }
    }

    private BigDecimal calculateMaxDrawdown(List<Trade> trades) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (Trade trade : trades) {
            runningTotal = runningTotal.add(trade.getProfitLoss());
            
            if (runningTotal.compareTo(peak) > 0) {
                peak = runningTotal;
            }
            
            BigDecimal drawdown = peak.subtract(runningTotal);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    private BigDecimal calculateSharpeRatio(List<Trade> trades) {
        if (trades.size() < 2) return BigDecimal.ZERO;

        // Calculate average return
        BigDecimal avgReturn = trades.stream()
            .map(Trade::getReturnPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(trades.size()), 4, RoundingMode.HALF_UP);

        // Calculate standard deviation
        BigDecimal variance = trades.stream()
            .map(trade -> trade.getReturnPercent().subtract(avgReturn).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(trades.size()), 4, RoundingMode.HALF_UP);

        BigDecimal stdDev = new BigDecimal(Math.sqrt(variance.doubleValue()));

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        // Assume risk-free rate of 2%
        BigDecimal riskFreeRate = new BigDecimal("2.0");
        return avgReturn.subtract(riskFreeRate).divide(stdDev, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateProfitFactor(List<Trade> trades) {
        BigDecimal grossProfit = trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
            .map(Trade::getProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossLoss = trades.stream()
            .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0)
            .map(Trade::getProfitLoss)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (grossLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);
    }

    // Inner classes for backtesting
    public static class Position {
        private String symbol;
        private LocalDateTime entryDate;
        private BigDecimal entryPrice;
        private BigDecimal shares;
        private BigDecimal entryValue;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        private String signalType;

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public LocalDateTime getEntryDate() { return entryDate; }
        public void setEntryDate(LocalDateTime entryDate) { this.entryDate = entryDate; }
        public BigDecimal getEntryPrice() { return entryPrice; }
        public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
        public BigDecimal getShares() { return shares; }
        public void setShares(BigDecimal shares) { this.shares = shares; }
        public BigDecimal getEntryValue() { return entryValue; }
        public void setEntryValue(BigDecimal entryValue) { this.entryValue = entryValue; }
        public BigDecimal getStopLoss() { return stopLoss; }
        public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
        public BigDecimal getTakeProfit() { return takeProfit; }
        public void setTakeProfit(BigDecimal takeProfit) { this.takeProfit = takeProfit; }
        public String getSignalType() { return signalType; }
        public void setSignalType(String signalType) { this.signalType = signalType; }
    }

    public static class Trade {
        private String symbol;
        private LocalDateTime entryDate;
        private LocalDateTime exitDate;
        private BigDecimal entryPrice;
        private BigDecimal exitPrice;
        private BigDecimal shares;
        private BigDecimal profitLoss;
        private BigDecimal returnPercent;
        private String signalType;

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public LocalDateTime getEntryDate() { return entryDate; }
        public void setEntryDate(LocalDateTime entryDate) { this.entryDate = entryDate; }
        public LocalDateTime getExitDate() { return exitDate; }
        public void setExitDate(LocalDateTime exitDate) { this.exitDate = exitDate; }
        public BigDecimal getEntryPrice() { return entryPrice; }
        public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
        public BigDecimal getExitPrice() { return exitPrice; }
        public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }
        public BigDecimal getShares() { return shares; }
        public void setShares(BigDecimal shares) { this.shares = shares; }
        public BigDecimal getProfitLoss() { return profitLoss; }
        public void setProfitLoss(BigDecimal profitLoss) { this.profitLoss = profitLoss; }
        public BigDecimal getReturnPercent() { return returnPercent; }
        public void setReturnPercent(BigDecimal returnPercent) { this.returnPercent = returnPercent; }
        public String getSignalType() { return signalType; }
        public void setSignalType(String signalType) { this.signalType = signalType; }
    }

    public static class BacktestResult {
        private BigDecimal initialCapital;
        private BigDecimal finalCapital;
        private BigDecimal totalReturn;
        private BigDecimal totalReturnPercent;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private List<Trade> trades;
        private int totalTrades;
        private int winningTrades;
        private int losingTrades;
        private BigDecimal winRate;
        private BigDecimal averageTradeReturn;
        private BigDecimal maxDrawdown;
        private BigDecimal sharpeRatio;
        private BigDecimal profitFactor;
        private BigDecimal averageWin;
        private BigDecimal averageLoss;

        // Getters and setters
        public BigDecimal getInitialCapital() { return initialCapital; }
        public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }
        public BigDecimal getFinalCapital() { return finalCapital; }
        public void setFinalCapital(BigDecimal finalCapital) { this.finalCapital = finalCapital; }
        public BigDecimal getTotalReturn() { return totalReturn; }
        public void setTotalReturn(BigDecimal totalReturn) { this.totalReturn = totalReturn; }
        public BigDecimal getTotalReturnPercent() { return totalReturnPercent; }
        public void setTotalReturnPercent(BigDecimal totalReturnPercent) { this.totalReturnPercent = totalReturnPercent; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public List<Trade> getTrades() { return trades; }
        public void setTrades(List<Trade> trades) { this.trades = trades; }
        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
        public int getWinningTrades() { return winningTrades; }
        public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }
        public int getLosingTrades() { return losingTrades; }
        public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }
        public BigDecimal getWinRate() { return winRate; }
        public void setWinRate(BigDecimal winRate) { this.winRate = winRate; }
        public BigDecimal getAverageTradeReturn() { return averageTradeReturn; }
        public void setAverageTradeReturn(BigDecimal averageTradeReturn) { this.averageTradeReturn = averageTradeReturn; }
        public BigDecimal getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        public BigDecimal getSharpeRatio() { return sharpeRatio; }
        public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
        public BigDecimal getProfitFactor() { return profitFactor; }
        public void setProfitFactor(BigDecimal profitFactor) { this.profitFactor = profitFactor; }
        public BigDecimal getAverageWin() { return averageWin; }
        public void setAverageWin(BigDecimal averageWin) { this.averageWin = averageWin; }
        public BigDecimal getAverageLoss() { return averageLoss; }
        public void setAverageLoss(BigDecimal averageLoss) { this.averageLoss = averageLoss; }
    }
} 