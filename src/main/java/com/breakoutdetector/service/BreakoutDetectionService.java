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
public class BreakoutDetectionService {

    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int ADX_PERIOD = 14;
    private static final int VOLUME_MA_PERIOD = 20;
    private static final int PRICE_MA_PERIOD = 20;
    private static final int RESISTANCE_LOOKBACK = 50;

    public List<BreakoutSignal> detectBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        if (stockDataList.size() < 50) {
            return signals; // Need sufficient data
        }

        // Sort by timestamp
        stockDataList.sort(Comparator.comparing(StockData::getTimestamp));
        
        // Detect different types of breakouts
        signals.addAll(detectVolumeBreakouts(stockDataList));
        signals.addAll(detectPriceBreakouts(stockDataList));
        signals.addAll(detectPriceRangeBreakouts(stockDataList));
        signals.addAll(detectTrendLineBreakouts(stockDataList));
        signals.addAll(detectRSIDivergence(stockDataList));
        signals.addAll(detectBollingerBandBreakouts(stockDataList));
        signals.addAll(detectMACDCrossovers(stockDataList));
        
        return signals;
    }

    private List<BreakoutSignal> detectVolumeBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = VOLUME_MA_PERIOD; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentVolume = currentData.getVolume().doubleValue();
            double currentPrice = currentData.getClose().doubleValue();
            
            // Calculate multiple volume averages
            double avgVolume20 = calculateVolumeMA(stockDataList, i, 20);
            double avgVolume50 = calculateVolumeMA(stockDataList, i, 50);
            double avgVolume10 = calculateVolumeMA(stockDataList, i, 10);
            
            // Calculate price change
            double priceChange = 0;
            if (i > 0) {
                double prevPrice = stockDataList.get(i - 1).getClose().doubleValue();
                priceChange = ((currentPrice - prevPrice) / prevPrice) * 100;
            }
            
            // Enhanced volume breakout conditions
            boolean isVolumeSpike = currentVolume > avgVolume20 * 2.0;
            boolean isVolumeTrend = currentVolume > avgVolume50 * 1.5;
            boolean isPriceRising = priceChange > 2.0;
            boolean isPriceFalling = priceChange < -2.0;
            
            // Volume breakout with price confirmation
            if (isVolumeSpike && isVolumeTrend) {
                String signalType = "VOLUME_BREAKOUT";
                String analysis = "";
                
                if (isPriceRising) {
                    signalType = "VOLUME_BREAKOUT_BULLISH";
                    analysis = "Bullish volume breakout with " + 
                        BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP) + 
                        "x average volume and " + BigDecimal.valueOf(priceChange).setScale(2, RoundingMode.HALF_UP) + "% price increase";
                } else if (isPriceFalling) {
                    signalType = "VOLUME_BREAKOUT_BEARISH";
                    analysis = "Bearish volume breakout with " + 
                        BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP) + 
                        "x average volume and " + BigDecimal.valueOf(priceChange).setScale(2, RoundingMode.HALF_UP) + "% price decrease";
                } else {
                    analysis = "Volume breakout with " + 
                        BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP) + "x average volume";
                }
                
                BreakoutSignal signal = new BreakoutSignal(
                    currentData.getSymbol(),
                    currentData.getTimestamp(),
                    signalType,
                    currentData.getClose(),
                    currentData.getClose(),
                    currentData.getVolume(),
                    BigDecimal.valueOf(avgVolume20).longValue()
                );
                
                signal.setVolumeRatio(BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP));
                signal.setAnalysis(analysis);
                
                signals.add(signal);
            }
            
            // Volume climax detection (extreme volume)
            if (currentVolume > avgVolume20 * 3.0) {
                BreakoutSignal signal = new BreakoutSignal(
                    currentData.getSymbol(),
                    currentData.getTimestamp(),
                    "VOLUME_CLIMAX",
                    currentData.getClose(),
                    currentData.getClose(),
                    currentData.getVolume(),
                    BigDecimal.valueOf(avgVolume20).longValue()
                );
                
                signal.setVolumeRatio(BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP));
                signal.setAnalysis("Volume climax detected with " + 
                    BigDecimal.valueOf(currentVolume / avgVolume20).setScale(2, RoundingMode.HALF_UP) + "x average volume");
                
                signals.add(signal);
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectPriceBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = RESISTANCE_LOOKBACK; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentPrice = currentData.getClose().doubleValue();
            
            // Find resistance level (highest price in lookback period)
            double resistanceLevel = findResistanceLevel(stockDataList, i, RESISTANCE_LOOKBACK);
            
            // Price breakout: current price > resistance level with volume confirmation
            if (currentPrice > resistanceLevel * 1.02) { // 2% above resistance
                StockData prevData = stockDataList.get(i - 1);
                double prevVolume = prevData.getVolume().doubleValue();
                double currentVolume = currentData.getVolume().doubleValue();
                
                // Volume should be above average
                if (currentVolume > prevVolume * 1.5) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "PRICE_BREAKOUT",
                        currentData.getClose(),
                        BigDecimal.valueOf(resistanceLevel),
                        currentData.getVolume(),
                        prevData.getVolume()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentVolume / prevVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Price breakout above resistance level " + 
                        BigDecimal.valueOf(resistanceLevel).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectPriceRangeBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = 50; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentPrice = currentData.getClose().doubleValue();
            
            // Calculate price range (high-low) for the last 20 days
            double[] priceRange = calculatePriceRange(stockDataList, i, 20);
            double rangeHigh = priceRange[0];
            double rangeLow = priceRange[1];
            double rangeMid = priceRange[2];
            
            // Check for breakout above the range
            if (currentPrice > rangeHigh * 1.02) { // 2% above range high
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.5) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "PRICE_RANGE_BREAKOUT",
                        currentData.getClose(),
                        BigDecimal.valueOf(rangeHigh),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Price range breakout above " + 
                        BigDecimal.valueOf(rangeHigh).setScale(2, RoundingMode.HALF_UP) + 
                        " (Range: " + BigDecimal.valueOf(rangeLow).setScale(2, RoundingMode.HALF_UP) + 
                        " - " + BigDecimal.valueOf(rangeHigh).setScale(2, RoundingMode.HALF_UP) + ")");
                    
                    signals.add(signal);
                }
            }
            
            // Check for breakdown below the range
            if (currentPrice < rangeLow * 0.98) { // 2% below range low
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.5) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "PRICE_RANGE_BREAKDOWN",
                        currentData.getClose(),
                        BigDecimal.valueOf(rangeLow),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Price range breakdown below " + 
                        BigDecimal.valueOf(rangeLow).setScale(2, RoundingMode.HALF_UP) + 
                        " (Range: " + BigDecimal.valueOf(rangeLow).setScale(2, RoundingMode.HALF_UP) + 
                        " - " + BigDecimal.valueOf(rangeHigh).setScale(2, RoundingMode.HALF_UP) + ")");
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectTrendLineBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = 100; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentPrice = currentData.getClose().doubleValue();
            
            // Calculate trend lines
            double[] trendLines = calculateTrendLines(stockDataList, i);
            double resistanceTrendLine = trendLines[0];
            double supportTrendLine = trendLines[1];
            
            // Check for breakout above resistance trend line
            if (resistanceTrendLine > 0 && currentPrice > resistanceTrendLine * 1.01) { // 1% above resistance
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.3) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "TRENDLINE_RESISTANCE_BREAKOUT",
                        currentData.getClose(),
                        BigDecimal.valueOf(resistanceTrendLine),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Trend line resistance breakout above " + 
                        BigDecimal.valueOf(resistanceTrendLine).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
            
            // Check for breakdown below support trend line
            if (supportTrendLine > 0 && currentPrice < supportTrendLine * 0.99) { // 1% below support
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.3) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "TRENDLINE_SUPPORT_BREAKDOWN",
                        currentData.getClose(),
                        BigDecimal.valueOf(supportTrendLine),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Trend line support breakdown below " + 
                        BigDecimal.valueOf(supportTrendLine).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectRSIDivergence(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = RSI_PERIOD + 10; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentRSI = calculateRSI(stockDataList, i, RSI_PERIOD);
            double currentPrice = currentData.getClose().doubleValue();
            
            // Look for bullish divergence: RSI making higher lows while price makes lower lows
            if (detectBullishDivergence(stockDataList, i)) {
                // Check if price is starting to move up
                if (currentRSI > 50 && currentPrice > stockDataList.get(i - 1).getClose().doubleValue()) {
                                    BreakoutSignal signal = new BreakoutSignal(
                    currentData.getSymbol(),
                    currentData.getTimestamp(),
                    "RSI_DIVERGENCE",
                    currentData.getClose(),
                    currentData.getClose(),
                    currentData.getVolume(),
                    BigDecimal.valueOf(calculateAverageVolume(stockDataList, i, 20)).longValue()
                );
                    
                    signal.setRsi(BigDecimal.valueOf(currentRSI).setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Bullish RSI divergence detected with RSI: " + 
                        BigDecimal.valueOf(currentRSI).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectBollingerBandBreakouts(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = 20; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            double currentPrice = currentData.getClose().doubleValue();
            
            // Calculate Bollinger Bands
            double[] bollingerBands = calculateBollingerBands(stockDataList, i, 20);
            double upperBand = bollingerBands[0];
            double middleBand = bollingerBands[1];
            double lowerBand = bollingerBands[2];
            
            // Breakout above upper Bollinger Band with volume
            if (currentPrice > upperBand) {
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.5) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "BOLLINGER_BREAKOUT",
                        currentData.getClose(),
                        BigDecimal.valueOf(upperBand),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Bollinger Band breakout above upper band: " + 
                        BigDecimal.valueOf(upperBand).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private List<BreakoutSignal> detectMACDCrossovers(List<StockData> stockDataList) {
        List<BreakoutSignal> signals = new ArrayList<>();
        
        for (int i = MACD_SLOW + MACD_SIGNAL; i < stockDataList.size(); i++) {
            StockData currentData = stockDataList.get(i);
            
            // Calculate MACD
            double[] macdValues = calculateMACD(stockDataList, i);
            double currentMACD = macdValues[0];
            double currentSignal = macdValues[1];
            double prevMACD = macdValues[2];
            double prevSignal = macdValues[3];
            
            // Bullish MACD crossover
            if (prevMACD <= prevSignal && currentMACD > currentSignal) {
                double avgVolume = calculateAverageVolume(stockDataList, i, 20);
                if (currentData.getVolume().doubleValue() > avgVolume * 1.2) {
                    BreakoutSignal signal = new BreakoutSignal(
                        currentData.getSymbol(),
                        currentData.getTimestamp(),
                        "MACD_CROSSOVER",
                        currentData.getClose(),
                        currentData.getClose(),
                        currentData.getVolume(),
                        BigDecimal.valueOf(avgVolume).longValue()
                    );
                    
                    signal.setMacd(BigDecimal.valueOf(currentMACD).setScale(2, RoundingMode.HALF_UP));
                    signal.setVolumeRatio(BigDecimal.valueOf(currentData.getVolume().doubleValue() / avgVolume)
                        .setScale(2, RoundingMode.HALF_UP));
                    signal.setAnalysis("Bullish MACD crossover detected with MACD: " + 
                        BigDecimal.valueOf(currentMACD).setScale(2, RoundingMode.HALF_UP));
                    
                    signals.add(signal);
                }
            }
        }
        
        return signals;
    }

    private double findResistanceLevel(List<StockData> stockDataList, int currentIndex, int lookback) {
        double maxPrice = 0;
        int startIndex = Math.max(0, currentIndex - lookback);
        
        for (int i = startIndex; i < currentIndex; i++) {
            double high = stockDataList.get(i).getHigh().doubleValue();
            if (high > maxPrice) {
                maxPrice = high;
            }
        }
        
        return maxPrice;
    }

    // Helper methods for technical analysis calculations
    
    private double calculateVolumeMA(List<StockData> stockDataList, int currentIndex, int period) {
        int startIndex = Math.max(0, currentIndex - period);
        double totalVolume = 0;
        int count = 0;
        
        for (int i = startIndex; i < currentIndex; i++) {
            totalVolume += stockDataList.get(i).getVolume().doubleValue();
            count++;
        }
        
        return count > 0 ? totalVolume / count : 0;
    }
    
    private double calculateRSI(List<StockData> stockDataList, int currentIndex, int period) {
        if (currentIndex < period) return 50.0;
        
        double gains = 0;
        double losses = 0;
        
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            double change = stockDataList.get(i).getClose().doubleValue() - 
                           stockDataList.get(i - 1).getClose().doubleValue();
            if (change > 0) {
                gains += change;
            } else {
                losses += Math.abs(change);
            }
        }
        
        double avgGain = gains / period;
        double avgLoss = losses / period;
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    private boolean detectBullishDivergence(List<StockData> stockDataList, int currentIndex) {
        // Simplified bullish divergence detection
        // Look for RSI making higher lows while price makes lower lows
        if (currentIndex < 20) return false;
        
        double currentRSI = calculateRSI(stockDataList, currentIndex, RSI_PERIOD);
        double currentPrice = stockDataList.get(currentIndex).getClose().doubleValue();
        
        // Find recent low points
        double minRSI = Double.MAX_VALUE;
        double minPrice = Double.MAX_VALUE;
        
        for (int i = currentIndex - 10; i < currentIndex; i++) {
            double rsiValue = calculateRSI(stockDataList, i, RSI_PERIOD);
            double priceValue = stockDataList.get(i).getClose().doubleValue();
            
            if (rsiValue < minRSI) minRSI = rsiValue;
            if (priceValue < minPrice) minPrice = priceValue;
        }
        
        // Check if RSI is making higher lows while price makes lower lows
        return currentRSI > minRSI && currentPrice < minPrice;
    }
    
    private double[] calculateBollingerBands(List<StockData> stockDataList, int currentIndex, int period) {
        // Calculate simple moving average
        double sma = 0;
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            sma += stockDataList.get(i).getClose().doubleValue();
        }
        sma /= period;
        
        // Calculate standard deviation
        double variance = 0;
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            double diff = stockDataList.get(i).getClose().doubleValue() - sma;
            variance += diff * diff;
        }
        variance /= period;
        double stdDev = Math.sqrt(variance);
        
        // Return [upper, middle, lower] bands
        return new double[]{
            sma + (2 * stdDev),  // Upper band
            sma,                  // Middle band
            sma - (2 * stdDev)   // Lower band
        };
    }
    
    private double[] calculateMACD(List<StockData> stockDataList, int currentIndex) {
        // Calculate EMA12 and EMA26
        double ema12 = calculateEMA(stockDataList, currentIndex, MACD_FAST);
        double ema26 = calculateEMA(stockDataList, currentIndex, MACD_SLOW);
        
        double currentMACD = ema12 - ema26;
        
        // Calculate signal line (EMA of MACD)
        double signal = calculateEMAOfMACD(stockDataList, currentIndex);
        
        // Calculate previous values
        double prevEma12 = calculateEMA(stockDataList, currentIndex - 1, MACD_FAST);
        double prevEma26 = calculateEMA(stockDataList, currentIndex - 1, MACD_SLOW);
        double prevMACD = prevEma12 - prevEma26;
        double prevSignal = calculateEMAOfMACD(stockDataList, currentIndex - 1);
        
        return new double[]{currentMACD, signal, prevMACD, prevSignal};
    }
    
    private double calculateEMA(List<StockData> stockDataList, int currentIndex, int period) {
        if (currentIndex < period) return stockDataList.get(currentIndex).getClose().doubleValue();
        
        double multiplier = 2.0 / (period + 1.0);
        double ema = stockDataList.get(currentIndex - period).getClose().doubleValue();
        
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            ema = (stockDataList.get(i).getClose().doubleValue() * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    private double calculateEMAOfMACD(List<StockData> stockDataList, int currentIndex) {
        // Simplified EMA calculation for MACD signal line
        if (currentIndex < MACD_SLOW + MACD_SIGNAL) return 0.0;
        
        double multiplier = 2.0 / (MACD_SIGNAL + 1.0);
        double ema = 0.0;
        
        // Calculate initial MACD values
        for (int i = currentIndex - MACD_SIGNAL + 1; i <= currentIndex; i++) {
            double ema12 = calculateEMA(stockDataList, i, MACD_FAST);
            double ema26 = calculateEMA(stockDataList, i, MACD_SLOW);
            double macd = ema12 - ema26;
            
            if (i == currentIndex - MACD_SIGNAL + 1) {
                ema = macd;
            } else {
                ema = (macd * multiplier) + (ema * (1 - multiplier));
            }
        }
        
                return ema;
    }
    
    private double[] calculatePriceRange(List<StockData> stockDataList, int currentIndex, int period) {
        double maxHigh = Double.MIN_VALUE;
        double minLow = Double.MAX_VALUE;
        
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            double high = stockDataList.get(i).getHigh().doubleValue();
            double low = stockDataList.get(i).getLow().doubleValue();
            
            if (high > maxHigh) maxHigh = high;
            if (low < minLow) minLow = low;
        }
        
        double rangeMid = (maxHigh + minLow) / 2.0;
        
        return new double[]{maxHigh, minLow, rangeMid};
    }
    
    private double[] calculateTrendLines(List<StockData> stockDataList, int currentIndex) {
        // Calculate resistance trend line (connecting highs)
        double resistanceTrendLine = calculateResistanceTrendLine(stockDataList, currentIndex);
        
        // Calculate support trend line (connecting lows)
        double supportTrendLine = calculateSupportTrendLine(stockDataList, currentIndex);
        
        return new double[]{resistanceTrendLine, supportTrendLine};
    }
    
    private double calculateResistanceTrendLine(List<StockData> stockDataList, int currentIndex) {
        // Find significant highs in the last 50 days
        List<Integer> highPoints = findSignificantHighs(stockDataList, currentIndex, 50);
        
        if (highPoints.size() < 2) return 0.0;
        
        // Calculate trend line using linear regression
        return calculateLinearRegression(stockDataList, highPoints, true);
    }
    
    private double calculateSupportTrendLine(List<StockData> stockDataList, int currentIndex) {
        // Find significant lows in the last 50 days
        List<Integer> lowPoints = findSignificantLows(stockDataList, currentIndex, 50);
        
        if (lowPoints.size() < 2) return 0.0;
        
        // Calculate trend line using linear regression
        return calculateLinearRegression(stockDataList, lowPoints, false);
    }
    
    private List<Integer> findSignificantHighs(List<StockData> stockDataList, int currentIndex, int lookback) {
        List<Integer> highs = new ArrayList<>();
        
        for (int i = currentIndex - lookback + 1; i <= currentIndex; i++) {
            if (i < 1 || i >= stockDataList.size() - 1) continue;
            
            double currentHigh = stockDataList.get(i).getHigh().doubleValue();
            double prevHigh = stockDataList.get(i - 1).getHigh().doubleValue();
            double nextHigh = stockDataList.get(i + 1).getHigh().doubleValue();
            
            // Check if this is a local high
            if (currentHigh > prevHigh && currentHigh > nextHigh) {
                highs.add(i);
            }
        }
        
        return highs;
    }
    
    private List<Integer> findSignificantLows(List<StockData> stockDataList, int currentIndex, int lookback) {
        List<Integer> lows = new ArrayList<>();
        
        for (int i = currentIndex - lookback + 1; i <= currentIndex; i++) {
            if (i < 1 || i >= stockDataList.size() - 1) continue;
            
            double currentLow = stockDataList.get(i).getLow().doubleValue();
            double prevLow = stockDataList.get(i - 1).getLow().doubleValue();
            double nextLow = stockDataList.get(i + 1).getLow().doubleValue();
            
            // Check if this is a local low
            if (currentLow < prevLow && currentLow < nextLow) {
                lows.add(i);
            }
        }
        
        return lows;
    }
    
    private double calculateLinearRegression(List<StockData> stockDataList, List<Integer> points, boolean isHigh) {
        if (points.size() < 2) return 0.0;
        
        // Use only the last 3 points for better trend line accuracy
        List<Integer> recentPoints = points.subList(Math.max(0, points.size() - 3), points.size());
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = recentPoints.size();
        
        for (int i = 0; i < n; i++) {
            int index = recentPoints.get(i);
            double x = index;
            double y = isHigh ? stockDataList.get(index).getHigh().doubleValue() 
                             : stockDataList.get(index).getLow().doubleValue();
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        
        // Calculate the trend line value at current index
        int currentIndex = stockDataList.size() - 1;
        return slope * currentIndex + intercept;
    }
    
    private double calculateAverageVolume(List<StockData> stockDataList, int currentIndex, int period) {
        int startIndex = Math.max(0, currentIndex - period);
        double totalVolume = 0;
        int count = 0;
        
        for (int i = startIndex; i < currentIndex; i++) {
            totalVolume += stockDataList.get(i).getVolume().doubleValue();
            count++;
        }
        
        return count > 0 ? totalVolume / count : 0;
    }

    public List<BreakoutSignal> filterSignals(List<BreakoutSignal> signals, Map<String, Object> filters) {
        return signals.stream()
            .filter(signal -> {
                // Filter by signal type
                if (filters.containsKey("signalType") && 
                    !signal.getSignalType().equals(filters.get("signalType"))) {
                    return false;
                }
                
                // Filter by minimum volume ratio
                if (filters.containsKey("minVolumeRatio") && 
                    signal.getVolumeRatio().compareTo((BigDecimal) filters.get("minVolumeRatio")) < 0) {
                    return false;
                }
                
                // Filter by minimum RSI
                if (filters.containsKey("minRSI") && signal.getRsi() != null &&
                    signal.getRsi().compareTo((BigDecimal) filters.get("minRSI")) < 0) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }
} 